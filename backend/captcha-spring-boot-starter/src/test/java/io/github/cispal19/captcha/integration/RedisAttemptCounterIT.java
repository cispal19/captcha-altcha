package io.github.cispal19.captcha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.domain.AttemptDimension;
import io.github.cispal19.captcha.domain.AttemptKey;
import io.github.cispal19.captcha.infrastructure.crypto.HmacSubjectHasher;
import io.github.cispal19.captcha.infrastructure.redis.RedisAttemptCounter;
import io.github.cispal19.captcha.integration.support.RedisSupport;
import java.time.Duration;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@DisplayName("RedisAttemptCounter: scripts Lua atómicos")
class RedisAttemptCounterIT {

  private static LettuceConnectionFactory factory;
  private static StringRedisTemplate redis;
  private static RedisAttemptCounter counter;
  private static final HmacSubjectHasher HASHER = new HmacSubjectHasher(new byte[32]);
  private static final Duration WINDOW = Duration.ofMinutes(15);

  @BeforeAll
  static void setUp() {
    factory = RedisSupport.connectionFactory();
    redis = RedisSupport.template(factory);
    counter = new RedisAttemptCounter(redis, "it-att:");
  }

  @AfterAll
  static void tearDown() {
    factory.destroy();
  }

  private static AttemptKey ip(String value) {
    return new AttemptKey(AttemptDimension.IP, HASHER.hash(value));
  }

  @Test
  @DisplayName("al 5.º fallo crea block:ip:{hash} con TTL ≈ block-duration y blockedFor lo expone")
  void blocks() {
    AttemptKey key = ip("192.168.0.10");
    for (int i = 1; i < 5; i++) {
      assertThat(counter.recordFailure(key, WINDOW, 5, Duration.ofMinutes(5))).isEqualTo(i);
    }
    assertThat(counter.recordFailure(key, WINDOW, 5, Duration.ofMinutes(5))).isZero();
    assertThat(redis.getExpire("it-att:block:ip:" + key.subjectHash())).isBetween(299L, 300L);
    assertThat(counter.blockedFor(key)).hasValueSatisfying(d -> assertThat(d).isPositive());
  }

  @Test
  @DisplayName("con block-duration=2s se desbloquea a los 3 s")
  void unblocks() throws InterruptedException {
    AttemptKey key = ip("192.168.0.11");
    counter.recordFailure(key, WINDOW, 1, Duration.ofSeconds(2));
    assertThat(counter.blockedFor(key)).isPresent();
    Thread.sleep(3000);
    assertThat(counter.blockedFor(key)).isEmpty();
  }

  @Test
  @DisplayName("100 incrementos concurrentes no se pierden; reset borra")
  void concurrent() {
    AttemptKey key = ip("192.168.0.12");
    IntStream.range(0, 100)
        .parallel()
        .forEach(i -> counter.recordFailure(key, WINDOW, 1000, Duration.ofMinutes(5)));
    assertThat(counter.failures(key)).isEqualTo(100);
    counter.reset(key);
    assertThat(counter.failures(key)).isZero();
  }

  @Test
  @DisplayName("rate:ip:{hash} cuenta por ventana de 60 s")
  void rate() {
    AttemptKey key = ip("192.168.0.13");
    for (int i = 1; i <= 31; i++) {
      assertThat(counter.countRequest(key, Duration.ofSeconds(60))).isEqualTo(i);
    }
    assertThat(redis.getExpire("it-att:rate:ip:" + key.subjectHash())).isBetween(59L, 60L);
  }

  @Test
  @DisplayName("en Redis nunca aparecen la IP ni el usuario en claro")
  void pseudonymized() {
    counter.recordFailure(ip("172.16.5.5"), WINDOW, 5, Duration.ofMinutes(5));
    counter.recordFailure(
        new AttemptKey(AttemptDimension.USER, HASHER.hash("jperez")),
        WINDOW,
        5,
        Duration.ofMinutes(5));
    Set<String> keys = redis.keys("it-att:*");
    assertThat(keys).isNotEmpty().noneMatch(k -> k.contains("172.16.5.5") || k.contains("jperez"));
  }
}
