package io.github.cispal19.captcha.infrastructure.logging;

import io.github.cispal19.captcha.application.port.VerificationEventLogger;
import io.github.cispal19.captcha.domain.AttemptDimension;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Eventos {@code captcha.verification} y {@code captcha.block}. */
public class Slf4jVerificationEventLogger implements VerificationEventLogger {

  private static final Logger LOG = LoggerFactory.getLogger("captcha.audit");
  private final Clock clock;

  public Slf4jVerificationEventLogger(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void verification(VerificationOutcome outcome, int cost, String subjectHash) {
    if (outcome == VerificationOutcome.VERIFIED) {
      LOG.info(
          "captcha.verification outcome={} cost={} subjectHash={} ts={}",
          outcome,
          cost,
          subjectHash,
          clock.instant());
    } else {
      LOG.warn(
          "captcha.verification outcome={} cost={} subjectHash={} ts={}",
          outcome,
          cost,
          subjectHash,
          clock.instant());
    }
  }

  @Override
  public void blocked(AttemptDimension dimension, String subjectHash, Duration duration) {
    LOG.warn(
        "captcha.block dimension={} subjectHash={} duration={} ts={}",
        dimension,
        subjectHash,
        duration,
        clock.instant());
  }
}
