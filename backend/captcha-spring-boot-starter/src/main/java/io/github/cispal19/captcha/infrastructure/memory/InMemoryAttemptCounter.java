package io.github.cispal19.captcha.infrastructure.memory;

import io.github.cispal19.captcha.application.port.AttemptCounter;
import io.github.cispal19.captcha.domain.AttemptKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Contadores locales con la misma semántica que {@code RedisAttemptCounter}. */
public class InMemoryAttemptCounter implements AttemptCounter {

  private record Window(long count, Instant expiresAt) {}

  private final Map<AttemptKey, Window> failures = new ConcurrentHashMap<>();
  private final Map<AttemptKey, Instant> blocks = new ConcurrentHashMap<>();
  private final Map<AttemptKey, Window> requests = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryAttemptCounter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public long recordFailure(
      AttemptKey key, Duration window, int maxAttempts, Duration blockDuration) {
    Instant now = clock.instant();
    Window updated = increment(failures, key, window, now);
    if (updated.count() >= maxAttempts) {
      blocks.put(key, now.plus(blockDuration));
      failures.remove(key);
      return 0;
    }
    return updated.count();
  }

  @Override
  public void reset(AttemptKey key) {
    failures.remove(key);
    blocks.remove(key);
  }

  @Override
  public long failures(AttemptKey key) {
    Window current = failures.get(key);
    return current == null || !current.expiresAt().isAfter(clock.instant()) ? 0 : current.count();
  }

  @Override
  public Optional<Duration> blockedFor(AttemptKey key) {
    Instant until = blocks.get(key);
    Instant now = clock.instant();
    if (until == null || !until.isAfter(now)) {
      return Optional.empty();
    }
    return Optional.of(Duration.between(now, until));
  }

  @Override
  public long countRequest(AttemptKey key, Duration window) {
    return increment(requests, key, window, clock.instant()).count();
  }

  private static Window increment(
      Map<AttemptKey, Window> map, AttemptKey key, Duration window, Instant now) {
    return map.compute(
        key,
        (k, current) ->
            current == null || !current.expiresAt().isAfter(now)
                ? new Window(1, now.plus(window))
                : new Window(current.count() + 1, current.expiresAt()));
  }
}
