package io.github.cispal19.captcha.infrastructure.memory;

import io.github.cispal19.captcha.application.port.ChallengeStore;
import io.github.cispal19.captcha.domain.ChallengeId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Almacén local para desarrollo y un solo pod; prohibido en producción. */
public class InMemoryChallengeStore implements ChallengeStore {

  private final Map<ChallengeId, Instant> challenges = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryChallengeStore(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void register(ChallengeId id, Duration ttl) {
    Instant now = clock.instant();
    challenges.values().removeIf(expiry -> !expiry.isAfter(now));
    challenges.putIfAbsent(id, now.plus(ttl));
  }

  @Override
  public boolean consume(ChallengeId id) {
    Instant expiry = challenges.remove(id);
    return expiry != null && expiry.isAfter(clock.instant());
  }
}
