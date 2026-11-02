# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y en versionado
semántico.

## [Unreleased]

### Fixed
- Los scripts Lua de `RedisAttemptCounter` se leen al cargar la clase con el classloader de la
  librería. Antes podían quedar ligados al classloader de una aplicación web ya detenida
  (`Could not load [captcha/lua/rate.lua]`).

## [1.0.0] - 2026-10-21

### Added
- Emisión de desafíos ALTCHA PoW v2 firmados con HMAC (`GET security.altcha.challenge-path`).
- Verificación antes de la autenticación mediante un filtro de servlet sobre `protected-paths`,
  con consumo único atómico en Redis (`GETDEL`) y TTL de 120 s.
- Límite de intentos por IP y por usuario (scripts Lua), bloqueo temporal con `Retry-After` y
  costo adaptativo.
- Integración con Spring Security: `BlockedLoginChecker` y listener de eventos de autenticación.
- Integración explícita (`filter.enabled=false`), `CaptchaVerificationException` y
  `CaptchaExceptionHandler`.
- Propiedades `security.altcha.*` validadas al arrancar; reglas de producción; arranque fallido
  si no hay Redis.
- Separación de claves con HKDF; seudonimización de IP y usuario.

### Security
- Se fijan `tomcat.version=11.0.27` y `jackson-bom.version=3.1.8` en el POM padre para mitigar
  CVE CRITICAL/HIGH del BOM de Spring Boot 4.1.1.
