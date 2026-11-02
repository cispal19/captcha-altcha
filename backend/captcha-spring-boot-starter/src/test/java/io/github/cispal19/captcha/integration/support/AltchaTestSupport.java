package io.github.cispal19.captcha.integration.support;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.altcha.altcha.v2.Altcha;
import org.json.JSONObject;

/** Utilidades de prueba: claves aleatorias y resolución real de desafíos ALTCHA. */
public final class AltchaTestSupport {

  private static final SecureRandom RANDOM = new SecureRandom();

  private AltchaTestSupport() {}

  /** Clave HMAC aleatoria de 48 bytes en hex (nunca versionada). */
  public static String randomKey() {
    byte[] bytes = new byte[48];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /** Resuelve el JSON de un desafío y devuelve el payload base64 para la cabecera. */
  public static String solve(String challengeJson) {
    try {
      JSONObject json = new JSONObject(challengeJson);
      JSONObject p = json.getJSONObject("parameters");
      Altcha.ChallengeParameters params =
          new Altcha.ChallengeParameters(
              p.getString("algorithm"),
              p.getString("nonce"),
              p.getString("salt"),
              p.getInt("cost"),
              p.getInt("keyLength"),
              p.getString("keyPrefix"),
              null,
              null,
              null,
              p.has("expiresAt") ? p.getLong("expiresAt") : null,
              null);
      Altcha.Challenge challenge = new Altcha.Challenge(params, json.getString("signature"));
      Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf(params.algorithm()));
      return encode(json, solution.counter(), solution.derivedKey());
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo resolver el desafío de prueba", e);
    }
  }

  /** Construye un payload con el contador y la clave derivada indicados. */
  public static String encode(JSONObject challenge, long counter, String derivedKey) {
    JSONObject solution =
        new JSONObject().put("counter", counter).put("derivedKey", derivedKey).put("time", 1.0);
    String payload =
        new JSONObject().put("challenge", challenge).put("solution", solution).toString();
    return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  /** Decodifica un payload base64 a JSON. */
  public static JSONObject decode(String payload) {
    return new JSONObject(new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8));
  }
}
