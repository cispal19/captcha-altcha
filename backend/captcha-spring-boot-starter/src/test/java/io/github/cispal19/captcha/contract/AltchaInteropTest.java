package io.github.cispal19.captcha.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.tools.AltchaFixtureGenerator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import org.altcha.altcha.v2.Altcha;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Interoperabilidad: un desafío creado en Java y resuelto con altcha-lib 2.7.0 (JS) en {@code
 * fixtures/altcha/payload.b64} verifica en Java; alterado, falla.
 */
@DisplayName("S-1 Interoperabilidad altcha-lib-java 2.1.1 ↔ altcha-lib 2.7.0")
class AltchaInteropTest {

  private static String payload;

  @BeforeAll
  static void load() throws Exception {
    payload = Files.readString(AltchaFixtureGenerator.fixturesDir().resolve("payload.b64")).trim();
  }

  private static Altcha.VerifySolutionResult verify(String p, String key) throws Exception {
    return Altcha.verifySolution(p, key, Altcha.pbkdf2());
  }

  @Test
  @DisplayName("la solución JS verifica en Java")
  void verifies() throws Exception {
    assertThat(verify(payload, AltchaFixtureGenerator.TEST_ONLY_KEY).verified()).isTrue();
  }

  @Test
  @DisplayName(
      "derivedKey alterada → invalidSolution; firma alterada → invalidSignature; otra clave → no verifica")
  void tampered() throws Exception {
    JSONObject json =
        new JSONObject(new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8));
    JSONObject badSolution = new JSONObject(json.toString());
    String key = badSolution.getJSONObject("solution").getString("derivedKey");
    badSolution
        .getJSONObject("solution")
        .put("derivedKey", (key.charAt(0) == 'f' ? "e" : "f") + key.substring(1));
    assertThat(verify(encode(badSolution), AltchaFixtureGenerator.TEST_ONLY_KEY).invalidSolution())
        .isTrue();

    JSONObject badSignature = new JSONObject(json.toString());
    String sig = badSignature.getJSONObject("challenge").getString("signature");
    badSignature
        .getJSONObject("challenge")
        .put("signature", (sig.charAt(0) == 'a' ? "b" : "a") + sig.substring(1));
    assertThat(
            verify(encode(badSignature), AltchaFixtureGenerator.TEST_ONLY_KEY).invalidSignature())
        .isTrue();

    assertThat(verify(payload, AltchaFixtureGenerator.TEST_ONLY_KEY.replace('0', '1')).verified())
        .isFalse();
  }

  private static String encode(JSONObject json) {
    return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("el desafío del fixture usa los nombres de campo del contrato")
  void schema() throws Exception {
    JSONObject challenge =
        new JSONObject(
            Files.readString(AltchaFixtureGenerator.fixturesDir().resolve("challenge.json")));
    assertThat(challenge.keySet()).containsExactlyInAnyOrder("parameters", "signature");
    assertThat(challenge.getJSONObject("parameters").keySet())
        .containsExactlyInAnyOrder(
            "algorithm", "nonce", "salt", "cost", "keyLength", "keyPrefix", "expiresAt");
  }
}
