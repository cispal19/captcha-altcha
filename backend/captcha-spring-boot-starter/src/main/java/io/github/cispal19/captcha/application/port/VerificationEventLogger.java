package io.github.cispal19.captcha.application.port;

import io.github.cispal19.captcha.domain.AttemptDimension;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Duration;

/** Eventos de auditoría estructurados; nunca recibe payloads, claves ni datos en claro. */
public interface VerificationEventLogger {

  void verification(VerificationOutcome outcome, int cost, String subjectHash);

  void blocked(AttemptDimension dimension, String subjectHash, Duration duration);
}
