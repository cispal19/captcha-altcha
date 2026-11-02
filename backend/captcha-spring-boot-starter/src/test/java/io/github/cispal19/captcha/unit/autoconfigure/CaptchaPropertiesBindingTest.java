package io.github.cispal19.captcha.unit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.autoconfigure.CaptchaProperties;
import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("CaptchaProperties: valores por defecto y clave obligatoria")
class CaptchaPropertiesBindingTest {

  @Configuration
  @EnableConfigurationProperties(CaptchaProperties.class)
  static class Props {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Props.class);

  @Test
  @DisplayName("con solo hmac-key, todos los valores por defecto coinciden con el contrato")
  void defaults() {
    runner
        .withPropertyValues("security.altcha.hmac-key=" + AltchaTestSupport.randomKey())
        .run(
            ctx -> {
              CaptchaProperties p = ctx.getBean(CaptchaProperties.class);
              assertThat(p.enabled()).isTrue();
              assertThat(p.allowDisableInProduction()).isFalse();
              assertThat(p.productionProfiles()).containsExactly("prod");
              assertThat(p.challengePath()).isEqualTo("/api/auth/challenge");
              assertThat(p.protectedPaths()).isEqualTo(List.of("POST /api/auth/login"));
              assertThat(p.headerName()).isEqualTo("X-Altcha");
              assertThat(p.challengeTtl()).isEqualTo(Duration.ofSeconds(120));
              assertThat(p.algorithm()).isEqualTo(CaptchaProperties.Algorithm.PBKDF2_SHA256);
              assertThat(p.baseCost()).isEqualTo(5000);
              assertThat(p.maxCost()).isEqualTo(80000);
              assertThat(p.maxAttempts()).isEqualTo(5);
              assertThat(p.attemptWindow()).isEqualTo(Duration.ofMinutes(15));
              assertThat(p.blockDuration()).isEqualTo(Duration.ofMinutes(5));
              assertThat(p.challengeRateLimit()).isEqualTo(30);
              assertThat(p.store()).isEqualTo(CaptchaProperties.Store.REDIS);
              assertThat(p.keyPrefix()).isEqualTo("captcha:");
              assertThat(p.filter().enabled()).isTrue();
              assertThat(p.filter().order()).isEqualTo(-110);
              assertThat(p.loginAttempts().autoRegister()).isTrue();
              assertThat(p.messages().invalid())
                  .isEqualTo("No se pudo verificar la solicitud. Inténtelo nuevamente.");
              assertThat(p.toString()).doesNotContain(p.hmacKey()).contains("hmacKey=***");
            });
  }

  @Test
  @DisplayName("sin hmac-key o con menos de 32 bytes el arranque falla nombrando la propiedad")
  void requiresKey() {
    runner.run(
        ctx ->
            assertThat(rootMessage(ctx.getStartupFailure())).contains("security.altcha.hmac-key"));
    runner
        .withPropertyValues("security.altcha.hmac-key=corta")
        .run(
            ctx ->
                assertThat(rootMessage(ctx.getStartupFailure()))
                    .contains("security.altcha.hmac-key"));
  }

  static String rootMessage(Throwable failure) {
    assertThat(failure).isNotNull();
    StringBuilder all = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      all.append(t.getMessage()).append('\n');
    }
    return all.toString();
  }
}
