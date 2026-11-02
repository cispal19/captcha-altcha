package io.github.cispal19.captcha.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Valor crudo de la cabecera con la solución: "No vacío, ≤ 4096 bytes, alfabeto base64 estricto".
 */
public record RawSolution(String value) {

  public static final int MAX_BYTES = 4096;

  private static final Pattern STRICT_BASE64 =
      Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");

  public RawSolution {
    if (!isValid(value)) {
      throw new IllegalArgumentException("Formato de solución inválido");
    }
  }

  public static Optional<RawSolution> from(String value) {
    return isValid(value) ? Optional.of(new RawSolution(value)) : Optional.empty();
  }

  private static boolean isValid(String value) {
    return value != null
        && !value.isEmpty()
        && value.length() <= MAX_BYTES
        && STRICT_BASE64.matcher(value).matches();
  }
}
