package io.github.cispal19.captcha.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Origen de una solicitud: IP (obligatoria) y usuario normalizado (opcional).
 *
 * <p>El usuario se normaliza "recortado y en minúsculas, con un máximo de 256 caracteres".
 */
public record Origin(String ip, Optional<String> username) {

  public static final int MAX_USERNAME_LENGTH = 256;

  public Origin {
    if (ip == null || ip.isBlank()) {
      throw new IllegalArgumentException("La IP de origen es obligatoria");
    }
    username = username == null ? Optional.empty() : username;
  }

  public static Origin of(String ip) {
    return new Origin(ip, Optional.empty());
  }

  public static Origin of(String ip, String username) {
    return new Origin(ip, normalizeUsername(username));
  }

  /** Normaliza un usuario: recortado, en minúsculas y con un máximo de 256 caracteres. */
  public static Optional<String> normalizeUsername(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    String normalized = username.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() > MAX_USERNAME_LENGTH) {
      throw new IllegalArgumentException(
          "El usuario excede " + MAX_USERNAME_LENGTH + " caracteres");
    }
    return Optional.of(normalized);
  }
}
