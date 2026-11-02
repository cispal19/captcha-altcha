package io.github.cispal19.captcha.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Identificador opaco de un desafío: {@code sha256(signature)} en hex (data-model §1). */
public record ChallengeId(String value) {

  private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

  public ChallengeId {
    if (value == null || !HEX_64.matcher(value).matches()) {
      throw new IllegalArgumentException("ChallengeId debe ser hex de 64 caracteres");
    }
  }

  public static ChallengeId fromSignature(String signature) {
    if (signature == null || signature.isBlank()) {
      throw new IllegalArgumentException("La firma del desafío es obligatoria");
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));
      return new ChallengeId(HexFormat.of().formatHex(digest));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
    }
  }
}
