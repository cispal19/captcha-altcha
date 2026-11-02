package io.github.cispal19.captcha.domain;

/** Resultado de verificar una solución. */
public enum VerificationOutcome {
  VERIFIED,
  INVALID_INPUT,
  INVALID_SIGNATURE,
  INVALID_SOLUTION,
  EXPIRED,
  NOT_FOUND_OR_USED,
  BLOCKED,
  STORE_UNAVAILABLE
}
