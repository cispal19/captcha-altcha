package io.github.cispal19.captcha.unit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cispal19.captcha.application.port.InvalidSolutionException;
import io.github.cispal19.captcha.application.port.IssuedChallenge;
import io.github.cispal19.captcha.domain.ChallengeId;
import io.github.cispal19.captcha.domain.RawSolution;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import io.github.cispal19.captcha.infrastructure.altcha.AltchaProofOfWorkEngine;
import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AltchaProofOfWorkEngine: firmas HMAC y verificación")
class AltchaProofOfWorkEngineTest {

  private static final String ALG = "PBKDF2/SHA-256";
  private final String secret = AltchaTestSupport.randomKey();
  private final AltchaProofOfWorkEngine engine = new AltchaProofOfWorkEngine(secret, ALG);

  private String solvedPayload(IssuedChallenge issued) {
    return AltchaTestSupport.solve(issued.json());
  }

  private VerificationOutcome verify(AltchaProofOfWorkEngine e, String payload) {
    return e.verify(e.parse(new RawSolution(payload)));
  }

  @Test
  @DisplayName("create usa el costo recibido, el algoritmo configurado y un id = sha256(firma)")
  void createChallenge() {
    IssuedChallenge issued = engine.create(1000, Duration.ofSeconds(120));
    JSONObject json = new JSONObject(issued.json());
    assertThat(json.getJSONObject("parameters").getInt("cost")).isEqualTo(1000);
    assertThat(json.getJSONObject("parameters").getString("algorithm")).isEqualTo(ALG);
    assertThat(issued.id()).isEqualTo(ChallengeId.fromSignature(json.getString("signature")));
    assertThat(issued.cost()).isEqualTo(1000);
  }

  @Test
  @DisplayName("solución válida → VERIFIED")
  void valid() {
    String payload = solvedPayload(engine.create(1000, Duration.ofSeconds(120)));
    assertThat(verify(engine, payload)).isEqualTo(VerificationOutcome.VERIFIED);
  }

  @Test
  @DisplayName("firma alterada → INVALID_SIGNATURE")
  void tamperedSignature() {
    JSONObject p =
        AltchaTestSupport.decode(solvedPayload(engine.create(1000, Duration.ofSeconds(60))));
    JSONObject ch = p.getJSONObject("challenge");
    String sig = ch.getString("signature");
    ch.put("signature", (sig.charAt(0) == 'a' ? "b" : "a") + sig.substring(1));
    String payload = encode(p);
    assertThat(verify(engine, payload)).isEqualTo(VerificationOutcome.INVALID_SIGNATURE);
  }

  @Test
  @DisplayName("costo alterado (firmado) → INVALID_SIGNATURE")
  void tamperedCost() {
    JSONObject p =
        AltchaTestSupport.decode(solvedPayload(engine.create(1000, Duration.ofSeconds(60))));
    p.getJSONObject("challenge").getJSONObject("parameters").put("cost", 1);
    assertThat(verify(engine, encode(p))).isEqualTo(VerificationOutcome.INVALID_SIGNATURE);
  }

  @Test
  @DisplayName("derivedKey alterada → INVALID_SOLUTION")
  void tamperedSolution() {
    JSONObject p =
        AltchaTestSupport.decode(solvedPayload(engine.create(1000, Duration.ofSeconds(60))));
    JSONObject sol = p.getJSONObject("solution");
    String key = sol.getString("derivedKey");
    sol.put("derivedKey", (key.charAt(0) == 'f' ? "e" : "f") + key.substring(1));
    sol.put("counter", sol.getLong("counter") + 1);
    assertThat(verify(engine, encode(p))).isEqualTo(VerificationOutcome.INVALID_SOLUTION);
  }

  @Test
  @DisplayName("desafío expirado → EXPIRED")
  void expired() throws InterruptedException {
    String payload = solvedPayload(engine.create(1000, Duration.ofSeconds(1)));
    Thread.sleep(2100);
    assertThat(verify(engine, payload)).isEqualTo(VerificationOutcome.EXPIRED);
  }

  @Test
  @DisplayName("otra clave → INVALID_SIGNATURE")
  void otherKey() {
    String payload = solvedPayload(engine.create(1000, Duration.ofSeconds(60)));
    AltchaProofOfWorkEngine other = new AltchaProofOfWorkEngine(AltchaTestSupport.randomKey(), ALG);
    assertThat(verify(other, payload)).isEqualTo(VerificationOutcome.INVALID_SIGNATURE);
  }

  @Test
  @DisplayName(
      "contador fuera de UINT32, derivedKey de longitud ≠ keyLength×2 o JSON no estricto → INVALID_INPUT")
  void invalidStructure() {
    JSONObject p =
        AltchaTestSupport.decode(solvedPayload(engine.create(1000, Duration.ofSeconds(60))));
    JSONObject outOfRange = new JSONObject(p.toString());
    outOfRange.getJSONObject("solution").put("counter", 4294967296L);
    JSONObject shortKey = new JSONObject(p.toString());
    shortKey.getJSONObject("solution").put("derivedKey", "abcd");
    JSONObject otherAlg = new JSONObject(p.toString());
    otherAlg.getJSONObject("challenge").getJSONObject("parameters").put("algorithm", "SHA-256");
    for (JSONObject bad : new JSONObject[] {outOfRange, shortKey, otherAlg}) {
      assertThatThrownBy(() -> engine.parse(new RawSolution(encode(bad))))
          .isInstanceOf(InvalidSolutionException.class);
    }
    String notJson =
        Base64.getEncoder().encodeToString("{challenge: 'x'}".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> engine.parse(new RawSolution(notJson)))
        .isInstanceOf(InvalidSolutionException.class);
  }

  private static String encode(JSONObject payload) {
    return Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
  }
}
