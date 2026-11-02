package io.github.cispal19.captcha.unit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.cispal19.captcha.application.port.ChallengeStore;
import io.github.cispal19.captcha.application.port.ProofOfWorkEngine;
import io.github.cispal19.captcha.application.service.AntiBotService;
import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.autoconfigure.CaptchaAutoConfiguration;
import io.github.cispal19.captcha.infrastructure.memory.InMemoryChallengeStore;
import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import io.github.cispal19.captcha.web.AntiBotChallengeController;
import io.github.cispal19.captcha.web.CaptchaExceptionHandler;
import io.github.cispal19.captcha.web.security.AuthenticationEventsListener;
import io.github.cispal19.captcha.web.security.BlockedLoginChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("Autoconfiguración: beans por defecto y reemplazables (OCP)")
class CaptchaAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CaptchaAutoConfiguration.class))
          .withPropertyValues(
              "security.altcha.hmac-key=" + AltchaTestSupport.randomKey(),
              "security.altcha.store=memory");

  @Test
  @DisplayName(
      "registra servicio, limitador, controlador, filtro, handler e integración de seguridad")
  void defaults() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).hasSingleBean(AntiBotService.class);
          assertThat(ctx).hasSingleBean(LoginAttemptLimiter.class);
          assertThat(ctx).hasSingleBean(AntiBotChallengeController.class);
          assertThat(ctx).hasSingleBean(CaptchaExceptionHandler.class);
          assertThat(ctx).hasSingleBean(BlockedLoginChecker.class);
          assertThat(ctx).hasSingleBean(AuthenticationEventsListener.class);
          assertThat(ctx.getBean(ChallengeStore.class)).isInstanceOf(InMemoryChallengeStore.class);
          assertThat(
                  ctx.getBean("antiBotVerificationFilter", FilterRegistrationBean.class).getOrder())
              .isEqualTo(-110);
        });
  }

  @Configuration
  static class CustomEngine {
    static final ProofOfWorkEngine ENGINE = mock(ProofOfWorkEngine.class);

    @Bean
    ProofOfWorkEngine customEngine() {
      return ENGINE;
    }
  }

  @Test
  @DisplayName("un bean propio del proyecto reemplaza al de la librería")
  void replaceable() {
    runner
        .withUserConfiguration(CustomEngine.class)
        .run(ctx -> assertThat(ctx.getBean(ProofOfWorkEngine.class)).isSameAs(CustomEngine.ENGINE));
  }
}
