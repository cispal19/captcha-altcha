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

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Reglas de arranque en perfiles productivos")
class ProductionSafetyTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DataRedisAutoConfiguration.class, CaptchaAutoConfiguration.class))
          .withPropertyValues("security.altcha.hmac-key=" + AltchaTestSupport.randomKey());

  @Test
  @DisplayName("prod + store=memory → falla el arranque")
  void memoryInProd() {
    runner
        .withPropertyValues("spring.profiles.active=prod", "security.altcha.store=memory")
        .run(
            ctx ->
                assertThat(CaptchaPropertiesBindingTest.rootMessage(ctx.getStartupFailure()))
                    .contains("security.altcha.store=memory"));
  }

  @Test
  @DisplayName("prod + enabled=false → falla el arranque")
  void disabledInProd() {
    runner
        .withPropertyValues(
            "spring.profiles.active=prod",
            "security.altcha.store=memory",
            "security.altcha.enabled=false",
            "security.altcha.production-profiles=prd")
        .run(ctx -> assertThat(ctx).hasNotFailed());
    runner
        .withPropertyValues(
            "spring.profiles.active=prd",
            "security.altcha.production-profiles=produccion,prd",
            "security.altcha.enabled=false",
            "spring.data.redis.host=localhost")
        .run(
            ctx ->
                assertThat(CaptchaPropertiesBindingTest.rootMessage(ctx.getStartupFailure()))
                    .contains("security.altcha.enabled=false"));
  }

  @Test
  @DisplayName("prod + enabled=false + allow-disable-in-production=true → arranca con WARN")
  void allowedDisable(CapturedOutput output) {
    runner
        .withPropertyValues(
            "spring.profiles.active=prod",
            "security.altcha.enabled=false",
            "security.altcha.allow-disable-in-production=true",
            "security.altcha.store=memory",
            "security.altcha.production-profiles=prod")
        .run(
            ctx ->
                assertThat(CaptchaPropertiesBindingTest.rootMessage(ctx.getStartupFailure()))
                    .contains("security.altcha.store=memory"));
    runner
        .withPropertyValues(
            "spring.profiles.active=prod",
            "security.altcha.enabled=false",
            "security.altcha.allow-disable-in-production=true",
            "spring.data.redis.host=localhost")
        .run(ctx -> assertThat(ctx).hasNotFailed());
    assertThat(output).contains("La verificación anti-bot está deshabilitada");
  }

  @Test
  @DisplayName("enabled=false fuera de producción → arranca y registra WARN")
  void disabledOutsideProdWarns(CapturedOutput output) {
    runner
        .withPropertyValues("security.altcha.enabled=false", "security.altcha.store=memory")
        .run(ctx -> assertThat(ctx).hasNotFailed());
    assertThat(output).contains("La verificación anti-bot está deshabilitada");
  }

  @Test
  @DisplayName("perfil local + store=memory → arranca")
  void memoryLocal() {
    runner
        .withPropertyValues("spring.profiles.active=local", "security.altcha.store=memory")
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }
}
