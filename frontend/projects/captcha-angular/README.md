# @cispal19/captcha-angular

Librería Angular 21 anti-bot basada en **ALTCHA Proof-of-Work v2**. El navegador resuelve una
prueba de trabajo en segundo plano y la librería la adjunta automáticamente a las peticiones
protegidas. No hay que leer, escuchar ni identificar nada (WCAG 2.1 AA).

Trabaja junto al starter `captcha-spring-boot-starter`.

## Instalación

```bash
npm install @cispal19/captcha-angular
```

```ts
// app.config.ts
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { captchaInterceptor, provideCaptcha } from '@cispal19/captcha-angular';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([captchaInterceptor])),
    provideCaptcha({ mode: 'checkbox' }),
  ],
};
```

### Modo casilla ("No soy un robot")

```html
<form [formGroup]="form" (ngSubmit)="login()">
  <!-- usuario y contraseña como siempre -->
  <captcha-checkbox formControlName="captcha" />
  <button type="submit">Ingresar</button>
</form>
```

El control es inválido (`captchaRequired`) hasta que la verificación termina. El interceptor
agrega `X-Altcha` a `POST /api/auth/login`, y el cuerpo de la petición no cambia.

### Modo invisible

```ts
provideCaptcha({ mode: 'invisible', privacyPolicyUrl: '/privacidad' });
```

```html
<captcha-notice />
```

El texto del aviso se cambia con `texts: { invisibleNotice: '...' }`. El enlace a la política de
privacidad solo aparece si se define `privacyPolicyUrl`.

Con `solveOn: 'load'` (el valor por defecto), la prueba se resuelve mientras el usuario escribe.
Si la solución expiró antes del envío, el interceptor la renueva sin intervención del usuario.

### Modo por formulario

```ts
provideCaptcha({
  mode: 'checkbox',
  privacyPolicyUrl: '/privacidad',
  protectedRequests: [
    { method: 'POST', url: '/api/auth/login' },                        // casilla
    { method: 'POST', url: '/api/public/consulta', mode: 'invisible' }, // invisible
  ],
});
```

## Configuración (`CaptchaConfig`)

| Opción | Defecto | Descripción |
|---|---|---|
| `mode` | `'checkbox'` | Modo global: `'checkbox'` o `'invisible'` |
| `challengeUrl` | `'/api/auth/challenge'` | Solo imprescindible en modo casilla; en invisible se aprende del backend |
| `protectedRequests` | `[{ method: 'POST', url: '/api/auth/login' }]` | Opcional. `url` puede ser un string o una `RegExp`; `mode` es opcional |
| `headerName` | `'X-Altcha'` | Si el backend indica otro en un 403, prevalece el del backend |
| `solveOn` | `'load'` | Solo modo invisible: `'load'` o `'submit'` |
| `solveTimeoutMs` | `30000` | Tiempo máximo de resolución |
| `workerFactory` | — | Ejecuta la prueba en un Web Worker (ver abajo) |
| `language` | `'es'` | `'es'` o `'en'` |
| `texts` | — | Reemplaza cualquier texto: `checkboxLabel`, `verifying`, `verified`, `error`, `retry`, `expired`, `blocked` (`{minutes}`), `unsupported`, `invisibleNotice`, `privacyLink`, `newWindow` |
| `privacyPolicyUrl` | — | Opcional. Si se define, `<captcha-notice>` muestra el enlace a la política de privacidad |

Una configuración inválida lanza un error `[captcha] …` al arrancar la aplicación.

## El backend define rutas y cabecera

Si el backend cambia `security.altcha.challenge-path`, `protected-paths` o `header-name`, su 403
`CAPTCHA_REQUIRED` indica `challengeUrl` y `headerName`. El interceptor resuelve con esos valores
y reintenta **una sola vez**.

- **Seguridad**: los valores del 403 solo se usan en peticiones del mismo origen que la
  aplicación. `challengeUrl` se acepta solo si es relativa o del mismo origen, y `headerName` solo
  si cumple `^[A-Za-z0-9-]{1,64}$`.
- **Modo casilla**: si cambia `challenge-path`, ponga la misma ruta en `challengeUrl`. Si no
  coinciden, la consola muestra:
  `[captcha] No se encontró el endpoint de desafío en "<url>". Verifique que CaptchaConfig.challengeUrl coincida con security.altcha.challenge-path del backend.`

## Web Worker (opcional)

Sin worker, la prueba se resuelve con WebCrypto asíncrono, que no bloquea la interfaz. Para usar
un worker, cree este archivo en su aplicación:

```ts
// src/app/captcha.worker.ts
import { installCaptchaWorker } from '@cispal19/captcha-angular/worker';
installCaptchaWorker();
```

```ts
provideCaptcha({
  workerFactory: () => new Worker(new URL('./captcha.worker', import.meta.url), { type: 'module' }),
});
```

Agregue también `"webWorkerTsConfig": "tsconfig.worker.json"` al `build` de su aplicación en
`angular.json`.

## Estilos (sistema de diseño)

| Variable | Defecto |
|---|---|
| `--captcha-color-primary` | `#0b5cad` |
| `--captcha-color-text` | `#1f2328` |
| `--captcha-color-error` | `#b42318` |
| `--captcha-color-background` | `#ffffff` |
| `--captcha-color-border` | `#6e7781` |
| `--captcha-radius` | `6px` |
| `--captcha-font-family` | `inherit` |
| `--captcha-gap` | `0.5rem` |

Los valores por defecto tienen un contraste de al menos 4.5:1. La librería no define estilos
globales.

## Estado y eventos

- `<captcha-checkbox>` emite `verified` (payload), `stateChange` y `failed` (`CaptchaError`).
- En modo invisible no hay componente: observe `CaptchaService.state` (signal), que pasa por
  `idle | verifying | verified | expired | blocked | error`.

## Errores (`CaptchaError.kind`)

| `kind` | Cuándo |
|---|---|
| `required` | Modo casilla sin casilla verificada (la petición no se envía) |
| `blocked` | 429 del backend; incluye `retryAfterSeconds` |
| `unavailable` | Servicio de desafíos no disponible |
| `timeout` | Se superó `solveTimeoutMs` |
| `unsupported` | Navegador sin WebCrypto o contexto no seguro (requiere HTTPS) |
| `not-found` | 404 al pedir el desafío |

Contrato completo: [`docs/contracts/angular-public-api.md`](../../../docs/contracts/angular-public-api.md).
