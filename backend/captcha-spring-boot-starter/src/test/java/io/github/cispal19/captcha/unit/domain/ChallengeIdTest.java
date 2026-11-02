package io.github.cispal19.captcha.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.cispal19.captcha.domain.ChallengeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChallengeId: identificador opaco")
class ChallengeIdTest {

  @Test
  @DisplayName("es sha256(signature) en hex minúsculas de 64 caracteres")
  void derivesFromSignature() {
    ChallengeId id = ChallengeId.fromSignature("abc");
    assertThat(id.value())
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        .hasSize(64);
  }

  @Test
  @DisplayName("es determinista y distinto para firmas distintas")
  void deterministic() {
    assertThat(ChallengeId.fromSignature("x")).isEqualTo(ChallengeId.fromSignature("x"));
    assertThat(ChallengeId.fromSignature("x")).isNotEqualTo(ChallengeId.fromSignature("y"));
  }

  @Test
  @DisplayName("rechaza valores que no son hex de 64")
  void rejectsInvalidValue() {
    assertThatIllegalArgumentException().isThrownBy(() -> new ChallengeId("XYZ"));
    assertThatIllegalArgumentException().isThrownBy(() -> ChallengeId.fromSignature(null));
  }
}
