package io.github.cispal19.captcha.application.port;

import io.github.cispal19.captcha.domain.ChallengeId;
import java.time.Duration;

/** Registro de desafíos emitidos con consumo único. */
public interface ChallengeStore {

  void register(ChallengeId id, Duration ttl);

  /** Consume el desafío de forma atómica; {@code true} solo la primera vez y si no expiró. */
  boolean consume(ChallengeId id);
}
