# Contrato: Propiedades del backend (`security.altcha.*`)

Clase: `CaptchaProperties`, anotada con `@ConfigurationProperties(prefix = "security.altcha")`
y `@Validated`. Es un record inmutable con valores por defecto, y Spring Boot genera los
metadatos para el autocompletado en el IDE.

Las propiedades se validan al arrancar: un valor inválido aborta el arranque con un mensaje que
nombra la propiedad y el motivo.

| Propiedad | Tipo | Defecto | Validación |
|---|---|---|---|
| `enabled` | boolean | `true` | Si es `false` con un perfil productivo activo, el arranque falla salvo `allow-disable-in-production=true` (se registra un WARN) |
| `allow-disable-in-production` | boolean | `false` | |
| `production-profiles` | list | `prod` | Perfiles que se consideran productivos |
| `hmac-key` | string (secreto) | — (**obligatoria**) | Al menos 32 bytes. Se inyecta como `${ALTCHA_HMAC_KEY}` desde un Secret de K8s. Se enmascara en actuator y en los logs |
| `challenge-path` | string | `/api/auth/challenge` | Empieza con `/` |
| `protected-paths` | list de `MÉTODO ruta` | `POST /api/auth/login` | Patrones Ant de Spring (`PathPatternParser`) |
| `header-name` | string | `X-Altcha` | Token de cabecera HTTP válido |
| `challenge-ttl` | Duration | `120s` | Entre `10s` y `10m` |
| `algorithm` | enum | `PBKDF2_SHA256` | `PBKDF2_SHA256`, `PBKDF2_SHA512`, `SHA256` |
| `base-cost` | int | `5000` | ≥ 1000. Ajustado para que la mediana de resolución sea < 1 s en un móvil de gama media |
| `max-cost` | int | `80000` | ≥ `base-cost` |
| `max-attempts` | int | `5` | 1 a 100 |
| `attempt-window` | Duration | `15m` | ≥ `block-duration` |
| `block-duration` | Duration | `5m` | `1m` a `24h` |
| `challenge-rate-limit` | int (por minuto e IP) | `30` | ≥ 1 |
| `store` | enum | `redis` | `redis` o `memory`. `memory` con un perfil productivo activo aborta el arranque |
| `key-prefix` | string | `captcha:` | No vacío. Debe ser único por aplicación si se comparte el Redis |
| `filter.enabled` | boolean | `true` | Con `false` no se registra el filtro; el proyecto invoca `AntiBotService` y `LoginAttemptLimiter` explícitamente desde su servicio de autenticación |
| `filter.order` | int | `-110` (`SecurityProperties.DEFAULT_FILTER_ORDER - 10`) | Debe ser menor que el orden de la cadena de Spring Security para verificar antes de autenticar; se registra un WARN si no lo es |
| `login-attempts.auto-register` | boolean | `true` | Con `false` no se escuchan los eventos de Spring Security; el proyecto llama a `recordFailure`/`reset` |
| `messages.required` | string | "Se requiere la verificación anti-bot." | No vacío |
| `messages.invalid` | string | "No se pudo verificar la solicitud. Inténtelo nuevamente." | No vacío |
| `messages.expired` | string | "La verificación expiró. Inténtelo nuevamente." | No vacío |
| `messages.too-many-attempts` | string | "Demasiados intentos. Intente nuevamente en unos minutos." | No vacío |
| `messages.unavailable` | string | "El servicio de verificación no está disponible. Inténtelo más tarde." | No vacío |

## Redis

- **Si el proyecto ya tiene Redis**, la librería reutiliza su conexión
  (`spring.data.redis.*`) sin configuración adicional; `key-prefix` aísla sus claves.
- **Si configura Redis por código** (un `@Bean RedisConnectionFactory` propio, sin propiedades),
  la librería lo detecta y lo reutiliza.
- **Si no tiene Redis** (ni propiedades ni bean propio) y `store=redis` (por defecto), el
  arranque falla con este mensaje:

  ```text
  security.altcha.store=redis requiere un servidor Redis y no se encontró ninguno configurado.
  Opciones:
    1. Configure spring.data.redis.host (o spring.data.redis.url) con su servidor Redis.
    2. Para desarrollo local: security.altcha.store=memory
  ```

- **Si está configurado pero no responde al arrancar**, se registra un WARN; en tiempo de
  ejecución se responde 503 (fail-closed).
- Con varios centros de datos, cada uno puede usar su propio Redis; el usuario debe atenderse siempre en el mismo.

## Ejemplo (`application.properties`)

```properties
security.altcha.hmac-key=${ALTCHA_HMAC_KEY}
security.altcha.protected-paths=POST /api/auth/login,POST /api/public/consulta
security.altcha.challenge-ttl=120s
security.altcha.max-attempts=5
spring.data.redis.host=${REDIS_HOST}
```

`application-local.properties`:

```properties
security.altcha.store=memory
```

## Beans expuestos (todos reemplazables con `@ConditionalOnMissingBean`)

| Bean | Uso por el consumidor |
|---|---|
| `AntiBotService` | `issueChallenge(origin)` y `verify(payload, origin) → VerificationOutcome`, para uso programático |
| `LoginAttemptLimiter` | `assertNotBlocked(username, ip)`, `recordFailure(...)`, `reset(...)` |
| `BlockedLoginChecker` | `UserDetailsChecker` para `DaoAuthenticationProvider.setPreAuthenticationChecks(...)` |
| `ChallengeStore`, `AttemptCounter`, `ProofOfWorkEngine` | Puntos de extensión (OCP) |
| `CaptchaVerificationException` | Modo explícito (`filter.enabled=false`): lanzarla con el `VerificationOutcome` cuando `verify` no devuelve `VERIFIED` |
| `CaptchaExceptionHandler` | `@RestControllerAdvice` que convierte `CaptchaVerificationException` y `LoginBlockedException` en la misma respuesta que el filtro; es reemplazable |
