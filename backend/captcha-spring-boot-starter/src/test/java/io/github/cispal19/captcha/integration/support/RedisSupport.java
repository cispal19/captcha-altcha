package io.github.cispal19.captcha.integration.support;

import java.util.Set;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis compartido por las pruebas de integración (se inicia una sola vez).
 *
 * <p>Orden de resolución:
 *
 * <ol>
 *   <li>{@code CAPTCHA_IT_REDIS_HOST} (y {@code CAPTCHA_IT_REDIS_PORT}, 6379 por defecto): usa ese
 *       Redis ya levantado.
 *   <li>Docker accesible: levanta un contenedor con Testcontainers.
 *   <li>Sin Docker (p. ej. Docker dentro de WSL en Windows): usa {@code localhost:6379}.
 * </ol>
 *
 * <p>Con un Redis externo borra al iniciar las claves {@code it-*} de corridas anteriores.
 */
public final class RedisSupport {

  private static final String EXTERNAL_HOST;
  private static final int EXTERNAL_PORT =
      Integer.parseInt(System.getenv().getOrDefault("CAPTCHA_IT_REDIS_PORT", "6379"));

  @SuppressWarnings("resource")
  private static final GenericContainer<?> REDIS;

  static {
    String host = System.getenv("CAPTCHA_IT_REDIS_HOST");
    if (host == null && !DockerClientFactory.instance().isDockerAvailable()) {
      host = "localhost";
    }
    EXTERNAL_HOST = host;
    if (host == null) {
      REDIS =
          new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
      REDIS.start();
    } else {
      REDIS = null;
      cleanExternal();
    }
  }

  private RedisSupport() {}

  public static String host() {
    return REDIS != null ? REDIS.getHost() : EXTERNAL_HOST;
  }

  public static int port() {
    return REDIS != null ? REDIS.getMappedPort(6379) : EXTERNAL_PORT;
  }

  public static LettuceConnectionFactory connectionFactory() {
    LettuceConnectionFactory factory =
        new LettuceConnectionFactory(new RedisStandaloneConfiguration(host(), port()));
    factory.afterPropertiesSet();
    factory.start();
    return factory;
  }

  public static StringRedisTemplate template(LettuceConnectionFactory factory) {
    StringRedisTemplate template = new StringRedisTemplate(factory);
    template.afterPropertiesSet();
    return template;
  }

  /**
   * Un Redis externo conserva bloqueos y contadores entre corridas; las pruebas esperan uno limpio.
   */
  private static void cleanExternal() {
    LettuceConnectionFactory factory = connectionFactory();
    try {
      StringRedisTemplate redis = template(factory);
      Set<String> keys = redis.keys("it-*");
      if (keys != null && !keys.isEmpty()) {
        redis.delete(keys);
      }
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Pruebas de integración: no se pudo conectar a Redis en "
              + EXTERNAL_HOST
              + ":"
              + EXTERNAL_PORT
              + " (CAPTCHA_IT_REDIS_HOST o, sin Docker accesible, localhost:6379)."
              + " Levante Redis o ajuste CAPTCHA_IT_REDIS_HOST/CAPTCHA_IT_REDIS_PORT.",
          e);
    } finally {
      factory.destroy();
    }
  }
}
