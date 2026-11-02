package io.github.cispal19.captcha.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.cispal19.captcha.application.port.VerificationEventLogger;
import io.github.cispal19.captcha.application.service.AttemptPolicy;
import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.application.service.LoginBlockedException;
import io.github.cispal19.captcha.domain.AttemptDimension;
import io.github.cispal19.captcha.infrastructure.memory.InMemoryAttemptCounter;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginAttemptLimiter: límites por IP y usuario y costo adaptativo")
class LoginAttemptLimiterTest {

  private final AttemptPolicy policy =
      new AttemptPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(5), 5000, 80000, 30);
  private VerificationEventLogger logger;
  private LoginAttemptLimiter limiter;

  @BeforeEach
  void setUp() {
    logger = mock(VerificationEventLogger.class);
    limiter =
        new LoginAttemptLimiter(
            new InMemoryAttemptCounter(Clock.systemUTC()), v -> "h(" + v + ")", policy, logger);
  }

  @Test
  @DisplayName("bloquea la IP al llegar a max-attempts y expone el tiempo restante")
  void blocksIpAfterMaxAttempts() {
    for (int i = 0; i < 4; i++) {
      limiter.recordIpFailure("1.1.1.1");
    }
    assertThatNoException().isThrownBy(() -> limiter.assertNotBlocked(null, "1.1.1.1"));
    limiter.recordIpFailure("1.1.1.1");
    assertThat(limiter.ipBlockedFor("1.1.1.1")).isPresent();
    assertThatThrownBy(() -> limiter.assertNotBlocked(null, "1.1.1.1"))
        .isInstanceOf(LoginBlockedException.class)
        .satisfies(
            e ->
                assertThat(((LoginBlockedException) e).retryAfter())
                    .isPositive()
                    .isLessThanOrEqualTo(Duration.ofMinutes(5)));
    verify(logger).blocked(AttemptDimension.IP, "h(1.1.1.1)", Duration.ofMinutes(5));
  }

  @Test
  @DisplayName("bloquea al usuario aunque cambie de IP")
  void blocksUserAcrossIps() {
    for (int i = 0; i < 5; i++) {
      limiter.recordFailure("JPerez", "10.0.0." + i);
    }
    assertThatThrownBy(() -> limiter.assertNotBlocked("jperez", "10.9.9.9"))
        .isInstanceOf(LoginBlockedException.class);
  }

  @Test
  @DisplayName("sin usuario solo registra la dimensión IP")
  void nullUsernameOnlyIp() {
    for (int i = 0; i < 5; i++) {
      limiter.recordFailure(null, "2.2.2.2");
    }
    assertThatThrownBy(() -> limiter.assertNotBlocked("otro", "2.2.2.2"))
        .isInstanceOf(LoginBlockedException.class);
    assertThatNoException().isThrownBy(() -> limiter.assertNotBlocked("otro", "3.3.3.3"));
  }

  @Test
  @DisplayName("reset reinicia solo la dimensión USER")
  void resetUser() {
    for (int i = 0; i < 3; i++) {
      limiter.recordFailure("ana", "4.4.4.4");
    }
    limiter.reset("ana");
    limiter.recordFailure("ana", "5.5.5.5");
    limiter.recordFailure("ana", "5.5.5.5");
    assertThatNoException().isThrownBy(() -> limiter.assertNotBlocked("ana", "6.6.6.6"));
    assertThat(limiter.costFor("4.4.4.4")).isEqualTo(5000 * 8);
  }

  @Test
  @DisplayName("costFor = min(base-cost × 2^fallos, max-cost)")
  void adaptiveCost() {
    assertThat(limiter.costFor("7.7.7.7")).isEqualTo(5000);
    limiter.recordIpFailure("7.7.7.7");
    assertThat(limiter.costFor("7.7.7.7")).isEqualTo(10000);
    for (int i = 0; i < 3; i++) {
      limiter.recordIpFailure("7.7.7.7");
    }
    assertThat(limiter.costFor("7.7.7.7")).isEqualTo(80000);
  }

  @Test
  @DisplayName("limita las solicitudes de desafío por IP (challenge-rate-limit)")
  void challengeRateLimit() {
    for (int i = 0; i < 30; i++) {
      limiter.checkChallengeRate("8.8.8.8");
    }
    assertThatThrownBy(() -> limiter.checkChallengeRate("8.8.8.8"))
        .isInstanceOf(LoginBlockedException.class);
    verify(logger, never()).blocked(any(), any(), any());
  }
}
