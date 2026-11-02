package io.github.cispal19.captcha.application.service;

import io.github.cispal19.captcha.application.port.AttemptCounter;
import io.github.cispal19.captcha.application.port.SubjectHasher;
import io.github.cispal19.captcha.application.port.VerificationEventLogger;
import io.github.cispal19.captcha.domain.AttemptDimension;
import io.github.cispal19.captcha.domain.AttemptKey;
import io.github.cispal19.captcha.domain.Origin;
import java.time.Duration;
import java.util.Optional;

/**
 * Controla intentos por IP y por usuario, el costo adaptativo y el límite de solicitudes de
 * desafío. Los sujetos se seudonimizan antes de llegar al almacén.
 */
public class LoginAttemptLimiter {

  private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
  private static final int MAX_COST_EXPONENT = 30;

  private final AttemptCounter counter;
  private final SubjectHasher hasher;
  private final AttemptPolicy policy;
  private final VerificationEventLogger logger;

  public LoginAttemptLimiter(
      AttemptCounter counter,
      SubjectHasher hasher,
      AttemptPolicy policy,
      VerificationEventLogger logger) {
    this.counter = counter;
    this.hasher = hasher;
    this.policy = policy;
    this.logger = logger;
  }

  /** Lanza {@link LoginBlockedException} si la IP o el usuario están bloqueados. */
  public void assertNotBlocked(String username, String ip) {
    Optional<Duration> ipBlock =
        ip == null || ip.isBlank() ? Optional.empty() : counter.blockedFor(ipKey(ip));
    Optional<Duration> userBlock = userKey(username).flatMap(counter::blockedFor);
    Duration longest =
        ipBlock
            .map(d -> userBlock.filter(u -> u.compareTo(d) > 0).orElse(d))
            .or(() -> userBlock)
            .orElse(null);
    if (longest != null) {
      throw new LoginBlockedException(longest);
    }
  }

  /** Registra un fallo de inicio de sesión en ambas dimensiones (sin usuario, solo IP). */
  public void recordFailure(String username, String ip) {
    if (ip != null && !ip.isBlank()) {
      recordIpFailure(ip);
    }
    userKey(username).ifPresent(this::record);
  }

  public void recordIpFailure(String ip) {
    record(ipKey(ip));
  }

  /** Reinicia el contador del usuario tras un inicio de sesión exitoso. */
  public void reset(String username) {
    userKey(username).ifPresent(counter::reset);
  }

  public Optional<Duration> ipBlockedFor(String ip) {
    return counter.blockedFor(ipKey(ip));
  }

  /** {@code min(base-cost × 2^fallos_recientes_ip, max-cost)}. */
  public int costFor(String ip) {
    long failures = Math.min(counter.failures(ipKey(ip)), MAX_COST_EXPONENT);
    long cost = (long) policy.baseCost() << failures;
    return (int) Math.min(cost, policy.maxCost());
  }

  /** Lanza {@link LoginBlockedException} si la IP supera {@code challenge-rate-limit}/min. */
  public void checkChallengeRate(String ip) {
    long requests = counter.countRequest(ipKey(ip), RATE_WINDOW);
    if (requests > policy.challengeRateLimit()) {
      throw new LoginBlockedException(RATE_WINDOW);
    }
  }

  public String subjectHash(String value) {
    return hasher.hash(value);
  }

  private void record(AttemptKey key) {
    long failures =
        counter.recordFailure(
            key, policy.attemptWindow(), policy.maxAttempts(), policy.blockDuration());
    if (failures == 0) {
      logger.blocked(key.dimension(), key.subjectHash(), policy.blockDuration());
    }
  }

  private AttemptKey ipKey(String ip) {
    return new AttemptKey(AttemptDimension.IP, hasher.hash(Origin.of(ip).ip()));
  }

  private Optional<AttemptKey> userKey(String username) {
    return Origin.normalizeUsername(username)
        .map(u -> new AttemptKey(AttemptDimension.USER, hasher.hash(u)));
  }
}
