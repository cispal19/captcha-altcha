# captcha-spring-boot-starter

Starter de Spring Boot 4.1 que protege peticiones (por defecto `POST /api/auth/login`) con una
**prueba de trabajo ALTCHA v2**. Emite desafíos firmados con HMAC, verifica la solución antes de
la autenticación, garantiza el consumo único con Redis y limita los intentos por IP y por usuario.

## Instalación

```xml
<dependency>
  <groupId>io.github.cispal19</groupId>
  <artifactId>captcha-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

Configuración mínima (`application.properties`):

```properties
security.altcha.hmac-key=${ALTCHA_HMAC_KEY}
spring.data.redis.host=${REDIS_HOST}
```

Con eso:
- `GET /api/auth/challenge` emite desafíos.
- `POST /api/auth/login` exige la cabecera `X-Altcha` antes de llegar a su controlador o filtro
  de Spring Security.

## Propiedades `security.altcha.*`

| Propiedad | Defecto | Descripción |
|---|---|---|
| `hmac-key` | — (**obligatoria**, ≥ 32 bytes) | Clave maestra. De ella se derivan con HKDF las subclaves de firma y de seudonimización |
| `enabled` | `true` | Con `false` no hay endpoint ni filtro. No se permite en perfiles productivos salvo `allow-disable-in-production=true` |
| `production-profiles` | `prod` | Perfiles en los que aplican las reglas de producción |
| `challenge-path` | `/api/auth/challenge` | Ruta del desafío |
| `protected-paths` | `POST /api/auth/login` | Lista `MÉTODO /ruta` (admite patrones `/**`) |
| `header-name` | `X-Altcha` | Cabecera con la solución |
| `challenge-ttl` | `120s` | Vigencia (10s a 10m) |
| `algorithm` | `pbkdf2-sha256` | `pbkdf2-sha256`, `pbkdf2-sha512` o `sha256` |
| `base-cost` / `max-cost` | `5000` / `80000` | Costo adaptativo: `min(base × 2^fallos_ip, max)` |
| `max-attempts` / `attempt-window` / `block-duration` | `5` / `15m` / `5m` | Bloqueo temporal por IP y por usuario |
| `challenge-rate-limit` | `30` | Desafíos por minuto e IP |
| `store` | `redis` | `redis` o `memory` (`memory` no se permite en producción) |
| `key-prefix` | `captcha:` | Prefijo de claves en Redis |
| `filter.enabled` / `filter.order` | `true` / `-110` | Verificación automática y su orden (antes de Spring Security, `-100`) |
| `login-attempts.auto-register` | `true` | Escucha los eventos de fallo y éxito de Spring Security |
| `messages.*` | textos en español | `required`, `invalid`, `expired`, `too-many-attempts`, `unavailable` |

Contrato completo: [`docs/contracts/backend-properties.md`](../../docs/contracts/backend-properties.md).

### Ejemplos por entorno

`application.properties` (producción y QA):

```properties
security.altcha.hmac-key=${ALTCHA_HMAC_KEY}
security.altcha.protected-paths=POST /api/auth/login,POST /api/public/consulta
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.ssl.enabled=true
```

`application-local.properties` (desarrollo sin Redis):

```properties
security.altcha.store=memory
```

## Redis

- **Si el proyecto ya tiene Redis**, la librería reutiliza su conexión (`spring.data.redis.*` o
  un `@Bean RedisConnectionFactory` propio). No necesita configuración adicional, y
  `key-prefix` aísla sus claves.
- **Si no hay Redis configurado** y `store=redis`, la aplicación no arranca y el mensaje indica
  cómo configurarlo o cómo usar `store=memory` en desarrollo.
- **Si Redis está configurado pero no responde al arrancar**, solo se registra un WARN. En
  ejecución se responde 503 (`CAPTCHA_UNAVAILABLE`, fail-closed).
- **Varios centros de datos o regiones**: si cada uno tiene su propio Redis, use también un
  Secret propio en cada uno, y enrute a cada usuario siempre al mismo (el desafío se guarda en el
  Redis donde se emitió).
- **Producción**: active TLS hacia Redis (`spring.data.redis.ssl.enabled=true`).

## Clave HMAC desde Kubernetes

```yaml
env:
  - name: ALTCHA_HMAC_KEY
    valueFrom:
      secretKeyRef:
        name: captcha-secret
        key: hmac-key
```

Genérela con `openssl rand -base64 48`. Debe ser la misma en todas las instancias que compartan Redis y distinta
por ambiente.

## Integración con Spring Security

El filtro se ejecuta antes de la cadena de Spring Security. Para bloquear también por usuario,
agregue una línea a su `DaoAuthenticationProvider`:

```java
provider.setPreAuthenticationChecks(blockedLoginChecker); // bean de la librería
```

Si construye su propio `ProviderManager`, configure el publicador de eventos, que es lo que
registra los fallos automáticamente:

```java
manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));
```

## Integración explícita (`filter.enabled=false`)

Para que su servicio de autenticación invoque los controles antes de comprobar las credenciales:

```java
String header = request.getHeader("X-Altcha");
if (header == null || header.isBlank()) {
  throw CaptchaVerificationException.required();
}
CaptchaVerificationException.throwIfNotVerified(antiBotService.verify(header, ClientOrigins.of(request)));
loginAttemptLimiter.assertNotBlocked(username, request.getRemoteAddr());
// ... autenticación actual
```

`CaptchaExceptionHandler` responde con el mismo `ProblemDetail` que el filtro, incluidos
`challengeUrl` y `headerName`, de modo que el frontend reintenta igual en ambos modos.

## Despliegue: WAF, proxy e ingress

- La cabecera configurada (`X-Altcha` por defecto) debe estar permitida en el WAF, el proxy
  inverso o el ingress.
- Detrás de un proxy, configure `server.forward-headers-strategy=framework` para que la IP de
  origen sea la del cliente.

## Errores (RFC 9457)

| Código | HTTP | Cuándo |
|---|---|---|
| `CAPTCHA_REQUIRED` | 403 | Falta la cabecera |
| `CAPTCHA_INVALID` | 403 | Firma, solución o formato inválidos, o desafío reutilizado |
| `CAPTCHA_EXPIRED` | 403 | Desafío expirado |
| `TOO_MANY_ATTEMPTS` | 429 + `Retry-After` | IP o usuario bloqueado, o exceso de solicitudes de desafío |
| `CAPTCHA_UNAVAILABLE` | 503 | Redis no disponible |
