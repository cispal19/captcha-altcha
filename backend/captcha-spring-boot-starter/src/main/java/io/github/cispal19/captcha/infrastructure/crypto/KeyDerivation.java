package io.github.cispal19.captcha.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Deriva con HKDF-SHA256 (RFC 5869) dos subclaves independientes a partir de {@code hmac-key}:
 * {@code captcha-challenge} (firma de desafíos) y {@code captcha-subject} (seudonimización).
 */
public final class KeyDerivation {

  public static final int MIN_MASTER_KEY_BYTES = 32;
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final int SUBKEY_BYTES = 32;

  private final String challengeSecret;
  private final byte[] subjectKey;

  private KeyDerivation(String challengeSecret, byte[] subjectKey) {
    this.challengeSecret = challengeSecret;
    this.subjectKey = subjectKey;
  }

  public static KeyDerivation fromMasterKey(String masterKey) {
    if (masterKey == null
        || masterKey.getBytes(StandardCharsets.UTF_8).length < MIN_MASTER_KEY_BYTES) {
      throw new IllegalArgumentException(
          "La clave maestra debe tener al menos " + MIN_MASTER_KEY_BYTES + " bytes");
    }
    byte[] ikm = masterKey.getBytes(StandardCharsets.UTF_8);
    byte[] challenge = hkdfSha256(ikm, new byte[0], info("captcha-challenge"), SUBKEY_BYTES);
    byte[] subject = hkdfSha256(ikm, new byte[0], info("captcha-subject"), SUBKEY_BYTES);
    return new KeyDerivation(HexFormat.of().formatHex(challenge), subject);
  }

  /** HKDF-SHA256 (extract + expand) según RFC 5869. */
  public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) {
    try {
      byte[] effectiveSalt = salt.length == 0 ? new byte[SUBKEY_BYTES] : salt;
      byte[] prk = hmac(effectiveSalt, ikm);
      byte[] okm = new byte[length];
      byte[] previous = new byte[0];
      int offset = 0;
      for (int counter = 1; offset < length; counter++) {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(prk, HMAC_SHA256));
        mac.update(previous);
        mac.update(info);
        mac.update((byte) counter);
        previous = mac.doFinal();
        int chunk = Math.min(previous.length, length - offset);
        System.arraycopy(previous, 0, okm, offset, chunk);
        offset += chunk;
      }
      return okm;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 no disponible en la JVM", e);
    }
  }

  static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
    Mac mac = Mac.getInstance(HMAC_SHA256);
    mac.init(new SecretKeySpec(key, HMAC_SHA256));
    return mac.doFinal(data);
  }

  private static byte[] info(String label) {
    return label.getBytes(StandardCharsets.US_ASCII);
  }

  /** Subclave en hex para firmar desafíos ALTCHA ({@code hmacSignatureSecret}). */
  public String challengeSecret() {
    return challengeSecret;
  }

  /** Subclave para seudonimizar sujetos. */
  public byte[] subjectKey() {
    return subjectKey.clone();
  }

  @Override
  public String toString() {
    return "KeyDerivation[***]";
  }
}
