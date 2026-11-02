package io.github.cispal19.captcha.unit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.domain.AttemptDimension;
import io.github.cispal19.captcha.domain.AttemptKey;
import io.github.cispal19.captcha.infrastructure.memory.InMemoryAttemptCounter;
import io.github.cispal19.captcha.unit.MutableClock;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryAttemptCounter: misma semántica que Redis")
class InMemoryAttemptCounterTest {

  private final MutableClock clock = new MutableClock();
  private final InMemoryAttemptCounter counter = new InMemoryAttemptCounter(clock);
  private final AttemptKey key = new AttemptKey(AttemptDimension.IP, "abc");
  private final Duration window = Duration.ofMinutes(15);
  private final Duration block = Duration.ofMinutes(5);

  @Test
  @DisplayName("bloquea al llegar a N, reinicia el contador y desbloquea al vencer")
  void blockAndUnblock() {
    for (int i = 1; i < 5; i++) {
      assertThat(counter.recordFailure(key, window, 5, block)).isEqualTo(i);
    }
    assertThat(counter.recordFailure(key, window, 5, block)).isZero();
    assertThat(counter.blockedFor(key)).contains(block);
    assertThat(counter.failures(key)).isZero();
    clock.advance(Duration.ofMinutes(5).plusSeconds(1));
    assertThat(counter.blockedFor(key)).isEmpty();
  }

  @Test
  @DisplayName("la ventana de fallos expira")
  void windowExpires() {
    counter.recordFailure(key, window, 5, block);
    clock.advance(window.plusSeconds(1));
    assertThat(counter.failures(key)).isZero();
  }

  @Test
  @DisplayName("100 incrementos concurrentes no se pierden; reset los borra")
  void concurrentAndReset() {
    IntStream.range(0, 100)
        .parallel()
        .forEach(i -> counter.recordFailure(key, window, 1000, block));
    assertThat(counter.failures(key)).isEqualTo(100);
    counter.reset(key);
    assertThat(counter.failures(key)).isZero();
  }

  @Test
  @DisplayName("countRequest usa ventana fija")
  void rate() {
    assertThat(counter.countRequest(key, Duration.ofMinutes(1))).isEqualTo(1);
    assertThat(counter.countRequest(key, Duration.ofMinutes(1))).isEqualTo(2);
    clock.advance(Duration.ofMinutes(1));
    assertThat(counter.countRequest(key, Duration.ofMinutes(1))).isEqualTo(1);
  }
}
