package io.github.cispal19.captcha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cispal19.captcha.application.port.StoreUnavailableException;
import io.github.cispal19.captcha.domain.ChallengeId;
import io.github.cispal19.captcha.infrastructure.redis.RedisChallengeStore;
import io.github.cispal19.captcha.integration.support.RedisSupport;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@DisplayName("RedisChallengeStore: GETDEL, TTL y concurrencia")
class RedisChallengeStoreIT {

  private static LettuceConnectionFactory factory;
  private static StringRedisTemplate redis;
  private static RedisChallengeStore store;

  @BeforeAll
  static void setUp() {
    factory = RedisSupport.connectionFactory();
    redis = RedisSupport.template(factory);
    store = new RedisChallengeStore(redis, "it-store:");
  }

  @AfterAll
  static void tearDown() {
    factory.destroy();
  }

  @Test
  @DisplayName("register crea {prefix}ch:{id} con TTL entre 119 y 120 s")
  void ttl() {
    ChallengeId id = ChallengeId.fromSignature("ttl");
    store.register(id, Duration.ofSeconds(120));
    assertThat(redis.getExpire("it-store:ch:" + id.value())).isBetween(119L, 120L);
  }

  @Test
  @DisplayName("consume devuelve true una vez y luego false")
  void singleUse() {
    ChallengeId id = ChallengeId.fromSignature("once");
    store.register(id, Duration.ofSeconds(120));
    assertThat(store.consume(id)).isTrue();
    assertThat(store.consume(id)).isFalse();
  }

  @Test
  @DisplayName("50 hilos consumiendo el mismo id → exactamente 1 éxito")
  void concurrent() throws Exception {
    ChallengeId id = ChallengeId.fromSignature("race");
    store.register(id, Duration.ofSeconds(120));
    ExecutorService pool = Executors.newFixedThreadPool(50);
    try {
      long ok =
          pool
              .invokeAll(
                  IntStream.range(0, 50)
                      .<Callable<Boolean>>mapToObj(i -> () -> store.consume(id))
                      .toList())
              .stream()
              .map(RedisChallengeStoreIT::get)
              .filter(Boolean::booleanValue)
              .count();
      assertThat(ok).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("con TTL de 2 s, a los 3 s ya no se puede consumir")
  void realExpiry() throws InterruptedException {
    ChallengeId id = ChallengeId.fromSignature("expira");
    store.register(id, Duration.ofSeconds(2));
    Thread.sleep(3000);
    assertThat(store.consume(id)).isFalse();
  }

  @Test
  @DisplayName("Redis inalcanzable → StoreUnavailableException")
  void unavailable() {
    LettuceConnectionFactory down = new LettuceConnectionFactory("127.0.0.1", 1);
    down.afterPropertiesSet();
    down.start();
    try {
      RedisChallengeStore broken = new RedisChallengeStore(RedisSupport.template(down), "x:");
      assertThatThrownBy(() -> broken.consume(ChallengeId.fromSignature("x")))
          .isInstanceOf(StoreUnavailableException.class);
    } finally {
      down.destroy();
    }
  }

  private static Boolean get(Future<Boolean> future) {
    try {
      return future.get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
