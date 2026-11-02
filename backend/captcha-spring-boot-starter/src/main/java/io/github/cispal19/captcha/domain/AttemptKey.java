package io.github.cispal19.captcha.domain;

/** Clave de un contador de intentos; el sujeto siempre va seudonimizado. */
public record AttemptKey(AttemptDimension dimension, String subjectHash) {

  public AttemptKey {
    if (dimension == null || subjectHash == null || subjectHash.isBlank()) {
      throw new IllegalArgumentException("AttemptKey requiere dimensión y hash del sujeto");
    }
  }
}
