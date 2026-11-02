package io.github.cispal19.captcha.application.service;

import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Duration;
import java.util.Optional;

/** Resultado de {@link AntiBotService#verify}; {@code retryAfter} solo aplica a BLOCKED. */
public record VerificationResult(VerificationOutcome outcome, Optional<Duration> retryAfter) {

  public static VerificationResult of(VerificationOutcome outcome) {
    return new VerificationResult(outcome, Optional.empty());
  }

  public boolean isVerified() {
    return outcome == VerificationOutcome.VERIFIED;
  }
}
