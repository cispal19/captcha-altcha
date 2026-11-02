package io.github.cispal19.captcha.autoconfigure;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.type.MethodMetadata;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Falla el arranque si {@code store=redis} y el proyecto no tiene Redis configurado ni por
 * propiedades ni por un {@code RedisConnectionFactory} propio. Si está configurado pero no
 * responde, solo advierte: en ejecución se aplica fail-closed.
 */
public final class RedisRequirementGuard implements SmartInitializingSingleton {

  static final String MISSING_REDIS_MESSAGE =
      """
      security.altcha.store=redis requiere un servidor Redis y no se encontró ninguno configurado.
      Opciones:
        1. Configure spring.data.redis.host (o spring.data.redis.url) con su servidor Redis.
        2. Para desarrollo local: security.altcha.store=memory""";

  private static final Logger LOG = LoggerFactory.getLogger(RedisRequirementGuard.class);
  private static final List<String> REDIS_PROPERTIES =
      List.of(
          "spring.data.redis.host",
          "spring.data.redis.url",
          "spring.data.redis.sentinel.master",
          "spring.data.redis.cluster.nodes",
          "spring.data.redis.masterreplica.nodes");
  private static final long PING_TIMEOUT_SECONDS = 2;

  private final ConfigurableListableBeanFactory beanFactory;

  public RedisRequirementGuard(
      Environment environment, ConfigurableListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
    boolean configuredByProperties =
        REDIS_PROPERTIES.stream().anyMatch(environment::containsProperty);
    if (!configuredByProperties && !hasProjectDefinedConnectionFactory()) {
      throw new IllegalStateException(MISSING_REDIS_MESSAGE);
    }
  }

  private boolean hasProjectDefinedConnectionFactory() {
    for (String name : beanFactory.getBeanNamesForType(RedisConnectionFactory.class, true, false)) {
      if (!beanFactory.containsBeanDefinition(name)) {
        return true;
      }
      BeanDefinition definition = beanFactory.getMergedBeanDefinition(name);
      String origin = originOf(definition);
      if (origin == null || !origin.startsWith("org.springframework.boot.")) {
        return true;
      }
    }
    return false;
  }

  private static String originOf(BeanDefinition definition) {
    if (definition instanceof AnnotatedBeanDefinition annotated) {
      MethodMetadata factoryMethod = annotated.getFactoryMethodMetadata();
      if (factoryMethod != null) {
        return factoryMethod.getDeclaringClassName();
      }
    }
    String resource = definition.getResourceDescription();
    if (resource != null && resource.contains("org/springframework/boot/")) {
      return "org.springframework.boot.";
    }
    return definition.getBeanClassName();
  }

  @Override
  public void afterSingletonsInstantiated() {
    RedisConnectionFactory factory =
        beanFactory.getBeanProvider(RedisConnectionFactory.class).getIfUnique();
    if (factory == null) {
      return;
    }
    try {
      CompletableFuture.runAsync(
              () -> {
                try (RedisConnection connection = factory.getConnection()) {
                  connection.ping();
                }
              })
          .get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOG.warn(
          "Redis configurado pero no respondió al arrancar; las verificaciones responderán 503"
              + " hasta que esté disponible (fail-closed).");
    }
  }
}
