package io.github.cispal19.captcha.infrastructure.redis;

import io.github.cispal19.captcha.application.port.ChallengeStore;
import io.github.cispal19.captcha.application.port.StoreUnavailableException;
import io.github.cispal19.captcha.domain.ChallengeId;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** {@code SET NX EX} al emitir y {@code GETDEL} atómico al consumir. */
public class RedisChallengeStore implements ChallengeStore {

  private final StringRedisTemplate redis;
  private final String keyPrefix;

  public RedisChallengeStore(StringRedisTemplate redis, String keyPrefix) {
    this.redis = redis;
    this.keyPrefix = keyPrefix;
  }

  @Override
  public void register(ChallengeId id, Duration ttl) {
    try {
      redis.opsForValue().setIfAbsent(key(id), "1", ttl);
    } catch (DataAccessException e) {
      throw new StoreUnavailableException("Redis no disponible al registrar el desafío", e);
    }
  }

  @Override
  public boolean consume(ChallengeId id) {
    try {
      return redis.opsForValue().getAndDelete(key(id)) != null;
    } catch (DataAccessException e) {
      throw new StoreUnavailableException("Redis no disponible al consumir el desafío", e);
    }
  }

  private String key(ChallengeId id) {
    return keyPrefix + "ch:" + id.value();
  }
}
