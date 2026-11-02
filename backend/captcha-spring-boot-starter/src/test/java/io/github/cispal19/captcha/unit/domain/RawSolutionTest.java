package io.github.cispal19.captcha.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.domain.RawSolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RawSolution: formato de la cabecera")
class RawSolutionTest {

  @Test
  @DisplayName("acepta base64 estricto válido")
  void acceptsValidBase64() {
    assertThat(RawSolution.from("eyJhIjoxfQ=="))
        .hasValueSatisfying(r -> assertThat(r.value()).isEqualTo("eyJhIjoxfQ=="));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "abc$", "ab=c", "abc", "a===", "eyJhIjoxfQ==\r\nX: y", "ñandú=="})
  @DisplayName("rechaza nulo, vacío, caracteres fuera del alfabeto y padding inválido")
  void rejectsInvalid(String value) {
    assertThat(RawSolution.from(value)).isEmpty();
  }

  @Test
  @DisplayName("rechaza más de 4096 bytes y acepta exactamente 4096")
  void enforcesMaxLength() {
    assertThat(RawSolution.from("A".repeat(4096))).isPresent();
    assertThat(RawSolution.from("A".repeat(4100))).isEmpty();
  }
}
