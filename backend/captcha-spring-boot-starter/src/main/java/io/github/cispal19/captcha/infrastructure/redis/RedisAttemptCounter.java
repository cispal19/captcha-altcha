package io.github.cispal19.captcha.infrastructure.redis;

import io.github.cispal19.captcha.application.port.AttemptCounter;
import io.github.cispal19.captcha.application.port.StoreUnavailableException;
import io.github.cispal19.captcha.domain.AttemptKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Contadores atómicos con scripts Lua, compartidos por todas las instancias. */
public class RedisAttemptCounter implements AttemptCounter {

  private static final DefaultRedisScript<Long> ATTEMPT = script("captcha/lua/attempt.lua");
  private static final DefaultRedisScript<Long> RATE = script("captcha/lua/rate.lua");

  private final StringRedisTemplate redis;
  private final String keyPrefix;

  public RedisAttemptCounter(StringRedisTemplate redis, String keyPrefix) {
    this.redis = redis;
    this.keyPrefix = keyPrefix;
  }

  @Override
  public long recordFailure(
      AttemptKey key, Duration window, int maxAttempts, Duration blockDuration) {
    return execute(
        () ->
            redis.execute(
                ATTEMPT,
                List.of(failKey(key), blockKey(key)),
                String.valueOf(window.toSeconds()),
                String.valueOf(maxAttempts),
                String.valueOf(blockDuration.toSeconds())));
  }

  @Override
  public void reset(AttemptKey key) {
    execute(() -> redis.delete(List.of(failKey(key), blockKey(key))));
  }

  @Override
  public long failures(AttemptKey key) {
    String value = execute(() -> redis.opsForValue().get(failKey(key)));
    return value == null ? 0 : Long.parseLong(value);
  }

  @Override
  public Optional<Duration> blockedFor(AttemptKey key) {
    Long millis =
        execute(() -> redis.getExpire(blockKey(key), java.util.concurrent.TimeUnit.MILLISECONDS));
    return millis == null || millis <= 0
        ? Optional.empty()
        : Optional.of(Duration.ofMillis(millis));
  }

  @Override
  public long countRequest(AttemptKey key, Duration window) {
    return execute(
        () ->
            redis.execute(
                RATE, List.of(prefixed("rate:", key)), String.valueOf(window.toSeconds())));
  }

  private String failKey(AttemptKey key) {
    return prefixed("fail:", key);
  }

  private String blockKey(AttemptKey key) {
    return prefixed("block:", key);
  }

  private String prefixed(String kind, AttemptKey key) {
    return keyPrefix
        + kind
        + key.dimension().name().toLowerCase(Locale.ROOT)
        + ":"
        + key.subjectHash();
  }

  private static <T> T execute(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException e) {
      throw new StoreUnavailableException("Redis no disponible para los contadores de intentos", e);
    }
  }

  /**
   * Lee el script al cargar la clase y con el classloader de la librería. Una {@link
   * ClassPathResource} sin classloader explícito captura el del hilo actual (en un servidor
   * embebido, el de la aplicación web), que deja de ser válido si ese contexto se detiene.
   */
  private static DefaultRedisScript<Long> script(String path) {
    ClassPathResource resource =
        new ClassPathResource(path, RedisAttemptCounter.class.getClassLoader());
    try (InputStream in = resource.getInputStream()) {
      DefaultRedisScript<Long> script =
          new DefaultRedisScript<>(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      script.setResultType(Long.class);
      return script;
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer el script Lua " + path, e);
    }
  }
}
