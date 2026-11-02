package io.github.cispal19.captcha.autoconfigure;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Reglas de arranque para perfiles productivos: prohíbe el almacén en memoria y la deshabilitación
 * salvo autorización explícita.
 */
public final class ProductionSafetyGuard {

  private static final Logger LOG = LoggerFactory.getLogger(ProductionSafetyGuard.class);

  public ProductionSafetyGuard(CaptchaProperties properties, Environment environment) {
    Set<String> productive = Set.copyOf(properties.productionProfiles());
    Set<String> active =
        Arrays.stream(environment.getActiveProfiles())
            .filter(productive::contains)
            .collect(Collectors.toUnmodifiableSet());
    boolean production = !active.isEmpty();
    if (production && properties.store() == CaptchaProperties.Store.MEMORY) {
      throw new IllegalStateException(
          "security.altcha.store=memory no está permitido con un perfil productivo activo "
              + active
              + ". Configure Redis (spring.data.redis.*).");
    }
    if (!properties.enabled()) {
      if (production && !properties.allowDisableInProduction()) {
        throw new IllegalStateException(
            "security.altcha.enabled=false no está permitido con un perfil productivo activo "
                + active
                + " salvo security.altcha.allow-disable-in-production=true.");
      }
      LOG.warn(
          "La verificación anti-bot está deshabilitada (security.altcha.enabled=false){}",
          production ? " EN PRODUCCIÓN por allow-disable-in-production=true" : "");
    }
  }
}
