package io.github.cispal19.captcha.unit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.infrastructure.crypto.HmacSubjectHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HmacSubjectHasher: seudonimización de IP y usuario")
class HmacSubjectHasherTest {

  private final HmacSubjectHasher hasher = new HmacSubjectHasher(new byte[32]);

  @Test
  @DisplayName("es determinista, en hex de 64 y nunca devuelve el valor en claro")
  void deterministicHex() {
    String h = hasher.hash("10.0.0.1");
    assertThat(h).hasSize(64).matches("[0-9a-f]+").doesNotContain("10.0.0.1");
    assertThat(hasher.hash("10.0.0.1")).isEqualTo(h);
    assertThat(hasher.hash("10.0.0.2")).isNotEqualTo(h);
  }

  @Test
  @DisplayName("claves distintas producen hashes distintos")
  void keyDependent() {
    byte[] other = new byte[32];
    other[0] = 1;
    assertThat(new HmacSubjectHasher(other).hash("u")).isNotEqualTo(hasher.hash("u"));
  }
}
