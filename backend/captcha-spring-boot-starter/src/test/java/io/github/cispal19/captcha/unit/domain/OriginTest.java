package io.github.cispal19.captcha.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.cispal19.captcha.domain.Origin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Origin: IP obligatoria y usuario normalizado")
class OriginTest {

  @Test
  @DisplayName("normaliza el usuario: recortado y en minúsculas")
  void normalizesUsername() {
    Origin origin = Origin.of("10.0.0.1", "  JPerez ");
    assertThat(origin.username()).contains("jperez");
    assertThat(origin.ip()).isEqualTo("10.0.0.1");
  }

  @Test
  @DisplayName("usuario ausente o en blanco queda vacío")
  void emptyUsername() {
    assertThat(Origin.of("10.0.0.1").username()).isEmpty();
    assertThat(Origin.of("10.0.0.1", "   ").username()).isEmpty();
  }

  @Test
  @DisplayName("rechaza usuarios de más de 256 caracteres")
  void rejectsLongUsername() {
    assertThat(Origin.of("1.1.1.1", "a".repeat(256)).username()).isPresent();
    assertThatIllegalArgumentException().isThrownBy(() -> Origin.of("1.1.1.1", "a".repeat(257)));
  }

  @Test
  @DisplayName("la IP es obligatoria")
  void requiresIp() {
    assertThatIllegalArgumentException().isThrownBy(() -> Origin.of(" "));
    assertThatIllegalArgumentException().isThrownBy(() -> Origin.of(null));
  }
}
