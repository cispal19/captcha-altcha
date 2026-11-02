package io.github.cispal19.captcha.autoconfigure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades {@code security.altcha.*}. Se validan al arrancar; un valor inválido aborta el
 * arranque nombrando la propiedad.
 */
@ConfigurationProperties(prefix = "security.altcha")
public record CaptchaProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("false") boolean allowDisableInProduction,
    @DefaultValue("prod") List<String> productionProfiles,
    String hmacKey,
    @DefaultValue("/api/auth/challenge") String challengePath,
    @DefaultValue("POST /api/auth/login") List<String> protectedPaths,
    @DefaultValue("X-Altcha") String headerName,
    @DefaultValue("120s") Duration challengeTtl,
    @DefaultValue("PBKDF2_SHA256") Algorithm algorithm,
    @DefaultValue("5000") int baseCost,
    @DefaultValue("80000") int maxCost,
    @DefaultValue("5") int maxAttempts,
    @DefaultValue("15m") Duration attemptWindow,
    @DefaultValue("5m") Duration blockDuration,
    @DefaultValue("30") int challengeRateLimit,
    @DefaultValue("redis") Store store,
    @DefaultValue("captcha:") String keyPrefix,
    @DefaultValue Filter filter,
    @DefaultValue LoginAttempts loginAttempts,
    @DefaultValue Messages messages) {

  public CaptchaProperties {
    CaptchaPropertiesValidator.validate(
        hmacKey,
        challengePath,
        protectedPaths,
        headerName,
        challengeTtl,
        baseCost,
        maxCost,
        maxAttempts,
        attemptWindow,
        blockDuration,
        challengeRateLimit,
        keyPrefix,
        messages);
    productionProfiles = List.copyOf(productionProfiles);
    protectedPaths = List.copyOf(protectedPaths);
  }

  /** Algoritmo de derivación de clave del desafío. */
  public enum Algorithm {
    PBKDF2_SHA256("PBKDF2/SHA-256"),
    PBKDF2_SHA512("PBKDF2/SHA-512"),
    SHA256("SHA-256");

    private final String altchaName;

    Algorithm(String altchaName) {
      this.altchaName = altchaName;
    }

    public String altchaName() {
      return altchaName;
    }
  }

  /** Almacén de desafíos y contadores. */
  public enum Store {
    REDIS,
    MEMORY
  }

  /** Verificación automática por filtro. */
  public record Filter(@DefaultValue("true") boolean enabled, @DefaultValue("-110") int order) {}

  /** Registro automático de fallos desde eventos de Spring Security. */
  public record LoginAttempts(@DefaultValue("true") boolean autoRegister) {}

  /** Textos de {@code detail} de las respuestas de error. */
  public record Messages(
      @DefaultValue("Se requiere la verificación anti-bot.") String required,
      @DefaultValue("No se pudo verificar la solicitud. Inténtelo nuevamente.") String invalid,
      @DefaultValue("La verificación expiró. Inténtelo nuevamente.") String expired,
      @DefaultValue("Demasiados intentos. Intente nuevamente en unos minutos.")
          String tooManyAttempts,
      @DefaultValue("El servicio de verificación no está disponible. Inténtelo más tarde.")
          String unavailable) {}

  @Override
  public String toString() {
    return "CaptchaProperties[enabled="
        + enabled
        + ", store="
        + store
        + ", challengePath="
        + challengePath
        + ", protectedPaths="
        + protectedPaths
        + ", hmacKey=***]";
  }
}
