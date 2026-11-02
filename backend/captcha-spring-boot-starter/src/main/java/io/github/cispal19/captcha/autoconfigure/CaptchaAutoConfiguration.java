package io.github.cispal19.captcha.autoconfigure;

import io.github.cispal19.captcha.application.port.AttemptCounter;
import io.github.cispal19.captcha.application.port.ChallengeStore;
import io.github.cispal19.captcha.application.port.ProofOfWorkEngine;
import io.github.cispal19.captcha.application.port.SubjectHasher;
import io.github.cispal19.captcha.application.port.VerificationEventLogger;
import io.github.cispal19.captcha.application.service.AntiBotService;
import io.github.cispal19.captcha.application.service.AttemptPolicy;
import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.infrastructure.altcha.AltchaProofOfWorkEngine;
import io.github.cispal19.captcha.infrastructure.crypto.HmacSubjectHasher;
import io.github.cispal19.captcha.infrastructure.crypto.KeyDerivation;
import io.github.cispal19.captcha.infrastructure.logging.Slf4jVerificationEventLogger;
import io.github.cispal19.captcha.infrastructure.memory.InMemoryAttemptCounter;
import io.github.cispal19.captcha.infrastructure.memory.InMemoryChallengeStore;
import io.github.cispal19.captcha.infrastructure.redis.RedisAttemptCounter;
import io.github.cispal19.captcha.infrastructure.redis.RedisChallengeStore;
import io.github.cispal19.captcha.web.AntiBotChallengeController;
import io.github.cispal19.captcha.web.AntiBotVerificationFilter;
import io.github.cispal19.captcha.web.CaptchaExceptionHandler;
import io.github.cispal19.captcha.web.CaptchaProblems;
import io.github.cispal19.captcha.web.ProtectedRequestMatcher;
import io.github.cispal19.captcha.web.security.AuthenticationEventsListener;
import io.github.cispal19.captcha.web.security.BlockedLoginChecker;
import io.github.cispal19.captcha.web.security.CaptchaSecurityExceptionHandler;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Autoconfiguración del starter. Todos los beans son reemplazables con
 * {@code @ConditionalOnMissingBean} (OCP).
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

  static final String PREFIX = "security.altcha";

  /** {@code SecurityProperties.DEFAULT_FILTER_ORDER} de Spring Boot. */
  static final int SECURITY_FILTER_ORDER = -100;

  private static final Logger LOG = LoggerFactory.getLogger(CaptchaAutoConfiguration.class);

  @Bean
  ProductionSafetyGuard captchaProductionSafetyGuard(
      CaptchaProperties properties, Environment environment) {
    return new ProductionSafetyGuard(properties, environment);
  }

  @Bean
  @ConditionalOnMissingBean
  Clock captchaClock() {
    return Clock.systemUTC();
  }

  @Bean
  KeyDerivation captchaKeyDerivation(CaptchaProperties properties) {
    return KeyDerivation.fromMasterKey(properties.hmacKey());
  }

  @Bean
  @ConditionalOnMissingBean
  SubjectHasher captchaSubjectHasher(KeyDerivation keys) {
    return new HmacSubjectHasher(keys.subjectKey());
  }

  @Bean
  @ConditionalOnMissingBean
  ProofOfWorkEngine captchaProofOfWorkEngine(CaptchaProperties properties, KeyDerivation keys) {
    return new AltchaProofOfWorkEngine(keys.challengeSecret(), properties.algorithm().altchaName());
  }

  @Bean
  @ConditionalOnMissingBean
  VerificationEventLogger captchaVerificationEventLogger(Clock clock) {
    return new Slf4jVerificationEventLogger(clock);
  }

  @Bean
  @ConditionalOnMissingBean
  LoginAttemptLimiter loginAttemptLimiter(
      AttemptCounter counter,
      SubjectHasher hasher,
      CaptchaProperties properties,
      VerificationEventLogger logger) {
    AttemptPolicy policy =
        new AttemptPolicy(
            properties.maxAttempts(),
            properties.attemptWindow(),
            properties.blockDuration(),
            properties.baseCost(),
            properties.maxCost(),
            properties.challengeRateLimit());
    return new LoginAttemptLimiter(counter, hasher, policy, logger);
  }

  @Bean
  @ConditionalOnMissingBean
  AntiBotService antiBotService(
      ProofOfWorkEngine engine,
      ChallengeStore store,
      LoginAttemptLimiter limiter,
      SubjectHasher hasher,
      VerificationEventLogger logger,
      CaptchaProperties properties) {
    return new AntiBotService(engine, store, limiter, hasher, logger, properties.challengeTtl());
  }

  @Bean
  @ConditionalOnMissingBean
  CaptchaProblems captchaProblems(CaptchaProperties properties) {
    CaptchaProperties.Messages m = properties.messages();
    return new CaptchaProblems(
        new CaptchaProblems.Messages(
            m.required(), m.invalid(), m.expired(), m.tooManyAttempts(), m.unavailable()),
        properties.challengePath(),
        properties.headerName());
  }

  /** Almacén Redis compartido (por defecto). */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(StringRedisTemplate.class)
  @ConditionalOnProperty(
      prefix = PREFIX,
      name = "store",
      havingValue = "redis",
      matchIfMissing = true)
  static class RedisStoreConfiguration {

    @Bean
    static RedisRequirementGuard captchaRedisRequirementGuard(
        Environment environment, ConfigurableListableBeanFactory beanFactory) {
      return new RedisRequirementGuard(environment, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    ChallengeStore captchaChallengeStore(
        RedisConnectionFactory connectionFactory, CaptchaProperties properties) {
      return new RedisChallengeStore(template(connectionFactory), properties.keyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    AttemptCounter captchaAttemptCounter(
        RedisConnectionFactory connectionFactory, CaptchaProperties properties) {
      return new RedisAttemptCounter(template(connectionFactory), properties.keyPrefix());
    }

    /** Plantilla interna: no se publica como bean para no competir con la del proyecto. */
    private static StringRedisTemplate template(RedisConnectionFactory connectionFactory) {
      StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
      template.afterPropertiesSet();
      return template;
    }
  }

  /** Almacén local para desarrollo (prohibido en producción). */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = PREFIX, name = "store", havingValue = "memory")
  static class MemoryStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ChallengeStore captchaChallengeStore(Clock clock) {
      return new InMemoryChallengeStore(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    AttemptCounter captchaAttemptCounter(Clock clock) {
      return new InMemoryAttemptCounter(clock);
    }
  }

  /** Endpoint del desafío, manejador de errores y filtro de verificación. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  static class WebConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CaptchaExceptionHandler captchaExceptionHandler(CaptchaProblems problems) {
      return new CaptchaExceptionHandler(problems);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
    static class EnabledWebConfiguration {

      @Bean
      @ConditionalOnMissingBean
      AntiBotChallengeController antiBotChallengeController(AntiBotService service) {
        return new AntiBotChallengeController(service);
      }

      @Bean
      @ConditionalOnProperty(
          prefix = PREFIX,
          name = "filter.enabled",
          havingValue = "true",
          matchIfMissing = true)
      FilterRegistrationBean<AntiBotVerificationFilter> antiBotVerificationFilter(
          AntiBotService service, CaptchaProblems problems, CaptchaProperties properties) {
        int order = properties.filter().order();
        if (order >= SECURITY_FILTER_ORDER) {
          LOG.warn(
              "security.altcha.filter.order={} no es menor que el orden de Spring Security ({}):"
                  + " la verificación podría ejecutarse después de la autenticación",
              order,
              SECURITY_FILTER_ORDER);
        }
        AntiBotVerificationFilter filter =
            new AntiBotVerificationFilter(
                service,
                ProtectedRequestMatcher.parse(properties.protectedPaths()),
                problems,
                properties.headerName());
        FilterRegistrationBean<AntiBotVerificationFilter> registration =
            new FilterRegistrationBean<>(filter);
        registration.setName("antiBotVerificationFilter");
        registration.setOrder(order);
        registration.addUrlPatterns("/*");
        return registration;
      }
    }
  }

  /** Integración opcional con Spring Security. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({
    UserDetailsChecker.class,
    AuthenticationSuccessEvent.class,
    WebAuthenticationDetails.class
  })
  static class SecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    BlockedLoginChecker blockedLoginChecker(LoginAttemptLimiter limiter) {
      return new BlockedLoginChecker(limiter);
    }

    @Bean
    @ConditionalOnProperty(
        prefix = PREFIX,
        name = "login-attempts.auto-register",
        havingValue = "true",
        matchIfMissing = true)
    AuthenticationEventsListener captchaAuthenticationEventsListener(LoginAttemptLimiter limiter) {
      return new AuthenticationEventsListener(limiter);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    CaptchaSecurityExceptionHandler captchaSecurityExceptionHandler(CaptchaProblems problems) {
      return new CaptchaSecurityExceptionHandler(problems);
    }
  }
}
