# Guía de integración (para personas y agentes de IA)

Sigue estos pasos **en orden** para proteger el login (u otro formulario) de un proyecto existente
con esta librería. Cada paso indica qué comprobar antes de seguir. Si algún requisito no se cumple,
**detente y avisa al usuario**; no fuerces la integración.

| Componente | Coordenadas | Registro |
|---|---|---|
| Backend | `io.github.cispal19:captcha-spring-boot-starter` | Maven Central |
| Frontend | `@cispal19/captcha-angular` | npmjs.com |

Versiones vigentes: consulta `backend/captcha-spring-boot-starter/CHANGELOG.md` y
`frontend/projects/captcha-angular/CHANGELOG.md` (o el registro).

---

## 0. Diagnóstico del proyecto destino (obligatorio)

Responde estas preguntas leyendo el proyecto antes de tocar nada:

| Pregunta | Dónde mirarlo | Requisito |
|---|---|---|
| ¿Versión de Spring Boot y de Java? | `pom.xml` / `build.gradle` | **Spring Boot 4.1+ y Java 21+**. Con Spring Boot 3.x el starter no funciona: avisa al usuario |
| ¿Versión de Angular? | `package.json` (`@angular/core`) | **Angular 21+**. Con versiones anteriores, avisa al usuario |
| ¿Tiene Redis? | `spring.data.redis.*` o un `@Bean RedisConnectionFactory` | Recomendado. Sin Redis solo vale `store=memory`, y únicamente en desarrollo |
| ¿Cómo autentica? | Busca `SecurityFilterChain`, `DaoAuthenticationProvider`, `AuthenticationManager` y el controlador del login | Define el caso A, B o C del paso 2 |
| ¿Ruta y método del login? | El controlador o la configuración de Spring Security | Por defecto se protege `POST /api/auth/login` |
| ¿El frontend llama al backend en el mismo origen? | Proxy de desarrollo, `environment.ts`, ingress | Las peticiones protegidas deben ser del mismo origen (o pasar por un proxy) |
| ¿Hay WAF, proxy inverso o ingress? | Infraestructura del proyecto | Deben dejar pasar la cabecera `X-Altcha` |

## 1. Backend: dependencia y configuración mínima

```xml
<dependency>
  <groupId>io.github.cispal19</groupId>
  <artifactId>captcha-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle: `implementation("io.github.cispal19:captcha-spring-boot-starter:1.0.0")`.

`application.properties`:

```properties
security.altcha.hmac-key=${ALTCHA_HMAC_KEY}
# Solo si la ruta del login no es POST /api/auth/login:
# security.altcha.protected-paths=POST /ruta/real/del/login
```

- **Redis**: si el proyecto ya lo tiene configurado, la librería reutiliza esa conexión; no añadas
  nada. Si no lo tiene y es solo para desarrollo, usa `security.altcha.store=memory` en el perfil
  local. `memory` no se permite en perfiles productivos (`prod`).
- **Clave**: genérala con `openssl rand -base64 48`. **Nunca** la escribas en el repositorio:
  debe llegar por variable de entorno o Secret.
- **Detrás de un proxy**: añade `server.forward-headers-strategy=framework` para obtener la IP real.

Referencia de todas las propiedades: `docs/contracts/backend-properties.md`.

## 2. Backend: encaje con la autenticación existente

Elige **un** caso según lo que encontraste en el paso 0.

**A. Spring Security con `DaoAuthenticationProvider`** (lo más habitual). El filtro de la
librería ya verifica el captcha antes de Spring Security, y los fallos de login se registran solos
mediante los eventos de autenticación. Añade una línea para bloquear también por usuario:

```java
@Bean
DaoAuthenticationProvider authProvider(UserDetailsService uds, PasswordEncoder enc,
                                       BlockedLoginChecker blockedLoginChecker) {
  DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
  provider.setPasswordEncoder(enc);
  provider.setPreAuthenticationChecks(blockedLoginChecker);
  return provider;
}
```

Si el proyecto construye su propio `ProviderManager`, configura también el publicador de eventos:
`manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));`

**B. Spring Security con otro mecanismo** (JWT emitido en un controlador que llama a
`AuthenticationManager.authenticate`). No requiere cambios: el filtro protege la ruta y los
eventos registran los fallos.

**C. Login hecho a mano, sin `AuthenticationManager`.** Desactiva el filtro
(`security.altcha.filter.enabled=false`) y llama a la librería desde el servicio de login:

```java
String header = request.getHeader("X-Altcha");
if (header == null || header.isBlank()) {
  throw CaptchaVerificationException.required();
}
CaptchaVerificationException.throwIfNotVerified(
    antiBotService.verify(header, ClientOrigins.of(request)));
loginAttemptLimiter.assertNotBlocked(username, request.getRemoteAddr());

boolean ok = /* autenticación actual del proyecto */;
if (ok) {
  loginAttemptLimiter.reset(username);
} else {
  loginAttemptLimiter.recordFailure(username, request.getRemoteAddr());
}
```

`CaptchaExceptionHandler` convierte las excepciones en el mismo `ProblemDetail` (403/429) que
produce el filtro.

**Si el proyecto tiene su propio `SecurityFilterChain`:** asegúrate de que la ruta del desafío
(`GET /api/auth/challenge` por defecto) sea accesible sin autenticar
(`requestMatchers("/api/auth/challenge").permitAll()`), igual que el login.

## 3. Frontend Angular

```bash
npm install @cispal19/captcha-angular
```

`app.config.ts`:

```ts
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { captchaInterceptor, provideCaptcha } from '@cispal19/captcha-angular';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([captchaInterceptor /* , interceptores existentes */])),
    provideCaptcha({
      mode: 'checkbox', // o 'invisible'
      // Solo si el login no es POST /api/auth/login:
      // protectedRequests: [{ method: 'POST', url: '/ruta/real/del/login' }],
    }),
  ],
};
```

Si el proyecto ya llama a `provideHttpClient(...)`, **añade** `captchaInterceptor` a su lista de
interceptores en vez de duplicar el provider.

Plantilla del login:

- **Modo casilla**: importa `CaptchaCheckboxComponent` en el componente y añade al formulario
  reactivo un control `captcha`:

  ```html
  <captcha-checkbox formControlName="captcha" />
  ```

  El control es inválido hasta que se verifica: el botón de envío debe respetar `form.invalid`.
- **Modo invisible**: importa `CaptchaNoticeComponent` y coloca `<captcha-notice />` cerca del
  botón. Opcional: `privacyPolicyUrl` en `provideCaptcha` para mostrar el enlace de privacidad.

El cuerpo de la petición **no cambia**: el interceptor solo añade la cabecera `X-Altcha`.

Manejo de errores en el componente de login:

- **403** con `code` `CAPTCHA_*`: el interceptor ya reintenta una vez. Si aun así falla, muestra
  un mensaje genérico.
- **429**: muestra "demasiados intentos" con la cabecera `Retry-After` (segundos).

Referencia completa: `frontend/projects/captcha-angular/README.md` y
`docs/contracts/angular-public-api.md`.

## 4. Despliegue

1. Crea el secreto con `openssl rand -base64 48` y exponlo como `ALTCHA_HMAC_KEY`. Debe ser el
   mismo en todas las instancias que comparten Redis y distinto en cada ambiente.
2. Activa TLS hacia Redis en producción: `spring.data.redis.ssl.enabled=true`.
3. Permite la cabecera `X-Altcha` en el WAF, el proxy y el ingress.

## 5. Verificación (obligatoria antes de dar la integración por terminada)

Con backend y frontend levantados:

```bash
# 1. Sin cabecera → 403 CAPTCHA_REQUIRED
curl -i -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{}'

# 2. El desafío responde 200 con JSON firmado
curl -i http://localhost:8080/api/auth/challenge
```

3. En el navegador: un login correcto funciona, y en DevTools la petición lleva `X-Altcha`.
4. Repetir con curl esa misma cabecera → 403 `CAPTCHA_INVALID` (consumo único).
5. Cinco contraseñas incorrectas → el 6.º intento responde 429 con `Retry-After`.
6. Las pruebas existentes del proyecto siguen pasando. Si alguna prueba de integración del login
   falla por falta de captcha, configura en el perfil de pruebas `security.altcha.enabled=false`
   (solo fuera de `prod`) o `security.altcha.store=memory`.

## Qué NO hacer

- No escribas la clave HMAC en el código, en `application.properties` ni en el repositorio.
- No uses `store=memory` ni `enabled=false` en producción; la aplicación se niega a arrancar.
- No modifiques el cuerpo de la petición de login para incluir el captcha: va en la cabecera.
- No copies el código fuente de la librería dentro del proyecto: usa las dependencias publicadas.
- No bajes de versión Spring Boot ni Angular para «hacerla encajar»; avisa al usuario.
