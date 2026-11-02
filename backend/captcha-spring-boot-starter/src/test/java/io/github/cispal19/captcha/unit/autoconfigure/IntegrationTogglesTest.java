package io.github.cispal19.captcha.unit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.application.service.AntiBotService;
import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.autoconfigure.CaptchaAutoConfiguration;
import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import io.github.cispal19.captcha.web.AntiBotChallengeController;
import io.github.cispal19.captcha.web.security.AuthenticationEventsListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Interruptores de integración: filtro, orden y registro automático")
class IntegrationTogglesTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CaptchaAutoConfiguration.class))
          .withPropertyValues(
              "security.altcha.hmac-key=" + AltchaTestSupport.randomKey(),
              "security.altcha.store=memory");

  @Test
  @DisplayName(
      "filter.enabled=false: sin filtro, con endpoint y servicios para invocación explícita")
  void filterDisabled() {
    runner
        .withPropertyValues("security.altcha.filter.enabled=false")
        .run(
            ctx -> {
              assertThat(ctx).doesNotHaveBean("antiBotVerificationFilter");
              assertThat(ctx).hasSingleBean(AntiBotChallengeController.class);
              assertThat(ctx).hasSingleBean(AntiBotService.class);
              assertThat(ctx).hasSingleBean(LoginAttemptLimiter.class);
            });
  }

  @Test
  @DisplayName("filter.order configurable, con WARN si no precede a Spring Security")
  void filterOrder(CapturedOutput output) {
    runner
        .withPropertyValues("security.altcha.filter.order=50")
        .run(
            ctx ->
                assertThat(
                        ctx.getBean("antiBotVerificationFilter", FilterRegistrationBean.class)
                            .getOrder())
                    .isEqualTo(50));
    assertThat(output)
        .contains("security.altcha.filter.order=50 no es menor que el orden de Spring Security");
  }

  @Test
  @DisplayName("login-attempts.auto-register=false: sin listener de eventos")
  void autoRegisterDisabled() {
    runner
        .withPropertyValues("security.altcha.login-attempts.auto-register=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(AuthenticationEventsListener.class));
  }
}
