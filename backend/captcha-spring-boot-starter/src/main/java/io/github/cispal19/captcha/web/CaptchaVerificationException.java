package io.github.cispal19.captcha.web;

import io.github.cispal19.captcha.application.service.VerificationResult;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Duration;
import java.util.Optional;

/**
 * Para la integración explícita ({@code filter.enabled=false}): el servicio de autenticación la
 * lanza cuando la verificación falla y {@link CaptchaExceptionHandler} responde igual que el
 * filtro.
 */
public class CaptchaVerificationException extends RuntimeException {

  private final VerificationOutcome outcome;
  private final transient Duration retryAfter;

  public CaptchaVerificationException(VerificationOutcome outcome) {
    this(outcome, null);
  }

  public CaptchaVerificationException(VerificationOutcome outcome, Duration retryAfter) {
    super("Verificación anti-bot fallida: " + outcome);
    this.outcome = outcome;
    this.retryAfter = retryAfter;
  }

  /** Atajo: lanza la excepción si el resultado no es VERIFIED. */
  public static void throwIfNotVerified(VerificationResult result) {
    if (!result.isVerified()) {
      throw new CaptchaVerificationException(result.outcome(), result.retryAfter().orElse(null));
    }
  }

  /** Para cabecera ausente en la integración explícita. */
  public static CaptchaVerificationException required() {
    return new CaptchaVerificationException(null, null);
  }

  public Optional<VerificationOutcome> outcome() {
    return Optional.ofNullable(outcome);
  }

  public Optional<Duration> retryAfter() {
    return Optional.ofNullable(retryAfter);
  }
}
