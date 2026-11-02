package io.github.cispal19.captcha.autoconfigure;

import io.github.cispal19.captcha.web.ProtectedRequestMatcher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/** Reglas de validación de {@link CaptchaProperties}; cada mensaje nombra la propiedad. */
final class CaptchaPropertiesValidator {

  private static final String P = "security.altcha.";
  private static final Pattern HEADER_TOKEN = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

  private CaptchaPropertiesValidator() {}

  static void validate(
      String hmacKey,
      String challengePath,
      List<String> protectedPaths,
      String headerName,
      Duration challengeTtl,
      int baseCost,
      int maxCost,
      int maxAttempts,
      Duration attemptWindow,
      Duration blockDuration,
      int challengeRateLimit,
      String keyPrefix,
      CaptchaProperties.Messages messages) {
    require(
        hmacKey != null && hmacKey.getBytes(StandardCharsets.UTF_8).length >= 32,
        "hmac-key es obligatoria y debe tener al menos 32 bytes (inyéctela desde un Secret, p. ej."
            + " ${ALTCHA_HMAC_KEY})");
    require(
        challengePath != null && challengePath.startsWith("/"),
        "challenge-path debe empezar con /");
    require(
        headerName != null && HEADER_TOKEN.matcher(headerName).matches(),
        "header-name debe ser un token de cabecera HTTP válido ([A-Za-z0-9-], máx. 64)");
    require(
        challengeTtl != null
            && challengeTtl.compareTo(Duration.ofSeconds(10)) >= 0
            && challengeTtl.compareTo(Duration.ofMinutes(10)) <= 0,
        "challenge-ttl debe estar entre 10s y 10m");
    require(baseCost >= 1000, "base-cost debe ser >= 1000");
    require(maxCost >= baseCost, "max-cost debe ser >= base-cost");
    require(maxAttempts >= 1 && maxAttempts <= 100, "max-attempts debe estar entre 1 y 100");
    require(
        blockDuration != null
            && blockDuration.compareTo(Duration.ofMinutes(1)) >= 0
            && blockDuration.compareTo(Duration.ofHours(24)) <= 0,
        "block-duration debe estar entre 1m y 24h");
    require(
        attemptWindow != null && attemptWindow.compareTo(blockDuration) >= 0,
        "attempt-window debe ser >= block-duration");
    require(challengeRateLimit >= 1, "challenge-rate-limit debe ser >= 1");
    require(keyPrefix != null && !keyPrefix.isBlank(), "key-prefix no puede estar vacío");
    try {
      ProtectedRequestMatcher.parse(protectedPaths);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(P + "protected-paths: " + e.getMessage(), e);
    }
    require(
        messages != null
            && notBlank(messages.required())
            && notBlank(messages.invalid())
            && notBlank(messages.expired())
            && notBlank(messages.tooManyAttempts())
            && notBlank(messages.unavailable()),
        "messages.* no puede contener textos vacíos");
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(P + message);
    }
  }
}
