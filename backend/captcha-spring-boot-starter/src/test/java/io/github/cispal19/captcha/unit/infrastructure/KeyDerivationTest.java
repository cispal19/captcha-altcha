package io.github.cispal19.captcha.unit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.cispal19.captcha.infrastructure.crypto.KeyDerivation;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Separación de claves con HKDF-SHA256")
class KeyDerivationTest {

  private static final HexFormat HEX = HexFormat.of();

  @Test
  @DisplayName("cumple el vector de prueba 1 de RFC 5869")
  void rfc5869TestCase1() {
    byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
    byte[] salt = HEX.parseHex("000102030405060708090a0b0c");
    byte[] info = HEX.parseHex("f0f1f2f3f4f5f6f7f8f9");
    byte[] okm = KeyDerivation.hkdfSha256(ikm, salt, info, 42);
    assertThat(HEX.formatHex(okm))
        .isEqualTo(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
  }

  @Test
  @DisplayName("deriva subclaves de 32 bytes distintas entre sí y de la maestra")
  void derivesIndependentSubkeys() {
    String master = "una-clave-maestra-de-al-menos-32-bytes!!";
    KeyDerivation keys = KeyDerivation.fromMasterKey(master);
    assertThat(keys.subjectKey()).hasSize(32);
    assertThat(keys.challengeSecret()).hasSize(64);
    assertThat(keys.challengeSecret()).isNotEqualTo(HEX.formatHex(keys.subjectKey()));
    assertThat(keys.challengeSecret())
        .isNotEqualTo(HEX.formatHex(master.getBytes(StandardCharsets.UTF_8)));
    assertThat(KeyDerivation.fromMasterKey(master).challengeSecret())
        .isEqualTo(keys.challengeSecret());
  }

  @Test
  @DisplayName("rechaza claves maestras de menos de 32 bytes")
  void rejectsShortMaster() {
    assertThatIllegalArgumentException().isThrownBy(() -> KeyDerivation.fromMasterKey("corta"));
  }

  @Test
  @DisplayName("toString no expone material de clave")
  void toStringMasked() {
    KeyDerivation keys = KeyDerivation.fromMasterKey("x".repeat(40));
    assertThat(keys.toString()).doesNotContain(keys.challengeSecret()).contains("***");
  }
}
