package io.github.cispal19.captcha.infrastructure.crypto;

import io.github.cispal19.captcha.application.port.SubjectHasher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** {@code HMAC-SHA256(subclave captcha-subject, valor)} en hex (data-model §1). */
public final class HmacSubjectHasher implements SubjectHasher {

  private final byte[] key;

  public HmacSubjectHasher(byte[] key) {
    this.key = key.clone();
  }

  @Override
  public String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(KeyDerivation.hmac(key, value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 no disponible en la JVM", e);
    }
  }
}
