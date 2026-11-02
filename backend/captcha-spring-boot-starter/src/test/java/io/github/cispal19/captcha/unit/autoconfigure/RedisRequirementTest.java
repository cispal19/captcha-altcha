package io.github.cispal19.captcha.unit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.autoconfigure.CaptchaAutoConfiguration;
import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Redis: reutilización y arranque fallido sin Redis")
class RedisRequirementTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DataRedisAutoConfiguration.class, CaptchaAutoConfiguration.class))
          .withPropertyValues("security.altcha.hmac-key=" + AltchaTestSupport.randomKey());

  @Test
  @DisplayName("store=redis sin propiedades ni bean propio → falla con el mensaje del contrato")
  void missingRedis() {
    runner.run(
        ctx ->
            assertThat(CaptchaPropertiesBindingTest.rootMessage(ctx.getStartupFailure()))
                .contains(
                    "security.altcha.store=redis requiere un servidor Redis y no se encontró"
                        + " ninguno configurado.")
                .contains("2. Para desarrollo local: security.altcha.store=memory"));
  }

  @Test
  @DisplayName("host configurado pero inalcanzable → arranca con WARN")
  void unreachable(CapturedOutput output) {
    runner
        .withPropertyValues("spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1")
        .run(ctx -> assertThat(ctx).hasNotFailed());
    assertThat(output).contains("Redis configurado pero no respondió al arrancar");
  }

  @Configuration
  static class ProjectRedis {
    @Bean
    RedisConnectionFactory projectRedisConnectionFactory() {
      return new LettuceConnectionFactory("127.0.0.1", 1);
    }
  }

  @Test
  @DisplayName("RedisConnectionFactory definido por el proyecto en código → se reutiliza")
  void projectDefinedFactory() {
    runner
        .withUserConfiguration(ProjectRedis.class)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).hasSingleBean(RedisConnectionFactory.class);
              assertThat(ctx).hasBean("projectRedisConnectionFactory");
            });
  }

  @Test
  @DisplayName("store=memory fuera de producción no requiere Redis")
  void memory() {
    runner
        .withPropertyValues("security.altcha.store=memory")
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }
}
