# Contrato: API pública de la librería Angular (`@cispal19/captcha-angular`)

Angular 21, standalone y basada en signals. Solo se exporta lo que aparece en este documento
(`public-api.ts`); todo lo demás es interno.

## Configuración

```ts
provideCaptcha(config: CaptchaConfig): EnvironmentProviders

interface CaptchaConfig {
  mode: 'checkbox' | 'invisible';             // modo global; por defecto 'checkbox'
  challengeUrl: string;                       // por defecto '/api/auth/challenge'; solo es imprescindible en modo casilla
  protectedRequests?: ReadonlyArray<{ method: string; url: string | RegExp; mode?: 'checkbox' | 'invisible' }>;
                                              // opcional (optimización); por defecto [{ method: 'POST', url: '/api/auth/login' }]
  headerName?: string;                        // por defecto 'X-Altcha'; si el backend indica otro en un 403, prevalece el del backend
  solveOn?: 'load' | 'submit';                // solo modo invisible; por defecto 'load'
  solveTimeoutMs?: number;                    // por defecto 30000
  workerFactory?: () => Worker;               // opcional; sin ella se resuelve en el hilo principal
  language?: 'es' | 'en';                     // por defecto 'es'
  texts?: Partial<CaptchaTexts>;              // reemplaza textos
  privacyPolicyUrl?: string;                  // obligatorio si mode = 'invisible'
}

interface CaptchaTexts {
  checkboxLabel: string;    // "No soy un robot"
  verifying: string;        // "Verificando…"
  verified: string;         // "Verificado"
  error: string;            // "No se pudo verificar. Reintentar"
  expired: string;          // "La verificación expiró. Vuelva a marcar la casilla"
  retry: string;            // "Reintentar"
  blocked: string;          // "Demasiados intentos. Intente en {minutes} min"
  unsupported: string;      // "Su navegador no permite completar la verificación. Use una conexión segura (HTTPS) o un navegador actualizado"
  invisibleNotice: string;  // "Este formulario está protegido contra bots."
  privacyLink: string;      // "Política de privacidad"
  newWindow: string;        // "(se abre en una ventana nueva)"
}
```

La configuración se valida en `provideCaptcha`: una configuración inválida lanza un error
descriptivo en el arranque de la aplicación.

## Interceptor

```ts
captchaInterceptor: HttpInterceptorFn   // se registra con provideHttpClient(withInterceptors([captchaInterceptor]))
```

Comportamiento con las peticiones que coinciden con `protectedRequests`:

1. Toma la solución vigente de `CaptchaService` (la casilla ya resuelta o la resolución
   anticipada) o, en modo invisible, resuelve en ese momento; si la solución vigente expiró o
   le quedan menos de 10 s, resuelve una nueva. El modo efectivo es `protectedRequests[i].mode`
   o, si no está definido, `mode`.
2. Agrega la cabecera `headerName` y no modifica el cuerpo.
3. Ante un 403 con `code = CAPTCHA_REQUIRED` o `CAPTCHA_EXPIRED` (incluso en solicitudes no
   declaradas en `protectedRequests`, pero **solo si la petición es del mismo origen que la
   app**), usa `challengeUrl` (solo si es relativo o del mismo origen) y `headerName` (solo si
   cumple `^[A-Za-z0-9-]{1,64}$`) de la respuesta, o los de la configuración si no son válidos,
   resuelve y reintenta **una sola vez**. En modo casilla sin casilla verificada no reintenta:
   emite `CaptchaError{kind:'required'}`.
4. Después de cada uso (exitoso o no), descarta la solución: es de un solo uso.
5. Si `GET challengeUrl` responde 404, escribe un diagnóstico con `console.error`. En
   modo invisible, la petición se envía igualmente sin cabecera para que el 403 del backend
   indique la ruta y la cabecera correctas.

Las peticiones no protegidas pasan sin cambios.

## Servicio

```ts
@Injectable({ providedIn: 'root' })
class CaptchaService {
  readonly state: Signal<CaptchaState>;     // 'idle' | 'verifying' | 'verified' | 'expired' | 'blocked' | 'error'
  solve(challengeUrl?: string): Promise<string>; // obtiene el desafío, lo resuelve y devuelve el payload base64
  takePayload(minValidityMs?: number): string | null; // payload vigente de un solo uso (luego null)
  solveForRequest(challengeUrl?: string): Promise<string>; // resuelve para una sola petición, sin guardarla (interceptor)
  readonly error: Signal<CaptchaError | null>;
  prefetch(): void;                         // resolución anticipada (la usa <captcha-notice> con solveOn='load')
  reset(): void;
}

interface CaptchaError {
  kind: 'required' | 'invalid' | 'expired' | 'blocked' | 'unavailable' | 'timeout' | 'unsupported' | 'not-found';
  retryAfterSeconds?: number;
}
```

En modo invisible no hay componente que emita eventos: el consumidor observa
`CaptchaService.state`.

```ts
```

## Componentes

| Selector | Entradas | Salidas | Uso |
|---|---|---|---|
| `<captcha-checkbox>` | `disabled` | `verified: string`, `stateChange: CaptchaState`, `failed: CaptchaError` | Modo casilla. `<input type="checkbox">` nativo, `aria-live="polite"` y foco visible. Implementa `ControlValueAccessor` para Reactive Forms (validador: verificado) |
| `<captcha-notice>` | — | — | Modo invisible: aviso de protección y enlace a la política de privacidad; con `solveOn='load'` inicia `prefetch()` al montarse |

Las salidas `verified`, `stateChange` y `failed` cubren los eventos de cambio de estado.

**Estilos**: variables CSS públicas `--captcha-color-primary`, `--captcha-color-text`,
`--captcha-color-error`, `--captcha-radius`, `--captcha-font-family` y `--captcha-gap`. No se
usa `ViewEncapsulation.None` y no hay estilos globales.

## Entrada secundaria

`@cispal19/captcha-angular/worker` exporta `installCaptchaWorker()` y no depende de Angular. Como
Web Worker, el proyecto consumidor declara su propio archivo de worker:

```ts
// src/app/captcha.worker.ts
import { installCaptchaWorker } from '@cispal19/captcha-angular/worker';
installCaptchaWorker();
```

```ts
workerFactory: () =>
  new Worker(new URL('./captcha.worker', import.meta.url), { type: 'module' })
```
