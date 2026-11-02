package io.github.cispal19.captcha.unit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("CaptchaProperties: cada regla aborta el arranque nombrando la propiedad")
class CaptchaPropertiesValidationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(CaptchaPropertiesBindingTest.Props.class)
          .withPropertyValues("security.altcha.hmac-key=" + AltchaTestSupport.randomKey());

  @ParameterizedTest(name = "{0}={1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "challenge-ttl|-5s",
        "challenge-ttl|9s",
        "challenge-ttl|11m",
        "base-cost|999",
        "max-cost|4000",
        "max-attempts|0",
        "max-attempts|101",
        "block-duration|59s",
        "block-duration|25h",
        "attempt-window|4m",
        "challenge-rate-limit|0",
        "key-prefix|' '",
        "header-name|X Altcha",
        "challenge-path|api/x",
        "protected-paths|LOGIN /x",
        "messages.invalid|' '"
      })
  void rejects(String property, String value) {
    runner
        .withPropertyValues("security.altcha." + property + "=" + value)
        .run(
            ctx -> {
              String message = CaptchaPropertiesBindingTest.rootMessage(ctx.getStartupFailure());
              String base =
                  property.contains(".") ? property.substring(0, property.indexOf('.')) : property;
              assertThat(message).contains("security.altcha." + base);
            });
  }
}
