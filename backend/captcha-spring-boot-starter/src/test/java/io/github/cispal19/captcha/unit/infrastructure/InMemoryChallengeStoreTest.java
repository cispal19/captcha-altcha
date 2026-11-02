package io.github.cispal19.captcha.unit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.domain.ChallengeId;
import io.github.cispal19.captcha.infrastructure.memory.InMemoryChallengeStore;
import io.github.cispal19.captcha.unit.MutableClock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryChallengeStore: consumo único y expiración")
class InMemoryChallengeStoreTest {

  private final MutableClock clock = new MutableClock();
  private final InMemoryChallengeStore store = new InMemoryChallengeStore(clock);
  private final ChallengeId id = ChallengeId.fromSignature("s1");

  @Test
  @DisplayName("consume devuelve true una sola vez")
  void singleUse() {
    store.register(id, Duration.ofSeconds(120));
    assertThat(store.consume(id)).isTrue();
    assertThat(store.consume(id)).isFalse();
  }

  @Test
  @DisplayName("un desafío expirado no se puede consumir")
  void expires() {
    store.register(id, Duration.ofSeconds(120));
    clock.advance(Duration.ofSeconds(121));
    assertThat(store.consume(id)).isFalse();
  }

  @Test
  @DisplayName("50 hilos concurrentes → exactamente 1 éxito")
  void concurrent() {
    store.register(id, Duration.ofSeconds(120));
    AtomicInteger ok = new AtomicInteger();
    IntStream.range(0, 50)
        .parallel()
        .forEach(
            i -> {
              if (store.consume(id)) {
                ok.incrementAndGet();
              }
            });
    assertThat(ok).hasValue(1);
  }

  @Test
  @DisplayName("un id no registrado no se puede consumir")
  void unknown() {
    assertThat(store.consume(ChallengeId.fromSignature("nunca"))).isFalse();
  }
}
