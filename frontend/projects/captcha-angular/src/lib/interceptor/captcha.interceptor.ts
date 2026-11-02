import {
  HttpContext,
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, defer, from, of, switchMap, throwError, catchError } from 'rxjs';
import { CAPTCHA_BYPASS, CAPTCHA_RETRIED } from '../api/captcha-bypass.token';
import { retryAfterSeconds } from '../api/challenge-api.client';
import {
  CAPTCHA_CONFIG,
  CaptchaError,
  CaptchaMode,
  ProtectedRequest,
  ResolvedCaptchaConfig,
} from '../config/captcha-config';
import { CaptchaService } from '../service/captcha.service';
import { isSameOrigin, pathOf, safeChallengeUrl, safeHeaderName } from './same-origin';

/** Margen mínimo de vigencia para reutilizar una solución anticipada. */
const MIN_VALIDITY_MS = 10_000;

/**
 * Adjunta la solución en la cabecera de las peticiones protegidas y, ante un 403
 * `CAPTCHA_REQUIRED`/`CAPTCHA_EXPIRED` del mismo origen, resuelve y reintenta una sola vez con la
 * ruta y la cabecera que indica el backend. El cuerpo no se modifica.
 */
export const captchaInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(CAPTCHA_BYPASS)) {
    return next(req);
  }
  const config = inject(CAPTCHA_CONFIG);
  const service = inject(CaptchaService);
  const rule = findRule(config.protectedRequests, req);
  const mode: CaptchaMode = rule?.mode ?? config.mode;

  const first$: Observable<HttpEvent<unknown>> = rule
    ? defer(() => from(obtain(service, mode))).pipe(
        switchMap((payload) =>
          payload === null ? next(req) : next(withHeader(req, config.headerName, payload, false)),
        ),
      )
    : next(req);

  return first$.pipe(catchError((error) => handleError(error, req, next, config, service, mode)));
};

function handleError(
  error: unknown,
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  config: ResolvedCaptchaConfig,
  service: CaptchaService,
  mode: CaptchaMode,
): Observable<HttpEvent<unknown>> {
  if (!(error instanceof HttpErrorResponse)) {
    return throwError(() => error);
  }
  if (error.status === 429) {
    service.markBlocked(retryAfterSeconds(error));
    return throwError(() => error);
  }
  if (error.status !== 403 || req.context.get(CAPTCHA_RETRIED) || !isSameOrigin(req.url)) {
    return throwError(() => error);
  }
  const body = (error.error ?? {}) as {
    code?: string;
    challengeUrl?: unknown;
    headerName?: unknown;
  };
  if (body.code === 'CAPTCHA_INVALID') {
    service.reset();
    return throwError(() => error);
  }
  if (body.code !== 'CAPTCHA_REQUIRED' && body.code !== 'CAPTCHA_EXPIRED') {
    return throwError(() => error);
  }
  const challengeUrl = safeChallengeUrl(body.challengeUrl) ?? config.challengeUrl;
  const headerName = safeHeaderName(body.headerName) ?? config.headerName;
  let payload$: Observable<string>;
  if (body.code === 'CAPTCHA_REQUIRED' && mode === 'checkbox') {
    const payload = service.takePayload();
    if (!payload) {
      return throwError(() => ({ kind: 'required' }) satisfies CaptchaError);
    }
    payload$ = of(payload);
  } else {
    payload$ = defer(() => from(service.solveForRequest(challengeUrl)));
  }
  return payload$.pipe(
    switchMap((payload) => next(withHeader(req, headerName, payload, true))),
    catchError((retryError) => {
      if (retryError instanceof HttpErrorResponse && retryError.status === 429) {
        service.markBlocked(retryAfterSeconds(retryError));
      }
      return throwError(() => retryError);
    }),
  );
}

/**
 * Solución para la primera petición. En modo invisible, si la ruta del desafío configurada no
 * existe (404), devuelve `null` para enviar sin cabecera: el 403 del backend indicará la ruta y la
 * cabecera correctas.
 */
async function obtain(service: CaptchaService, mode: CaptchaMode): Promise<string | null> {
  if (mode === 'checkbox') {
    const payload = service.takePayload();
    if (!payload) {
      throw { kind: 'required' } satisfies CaptchaError;
    }
    return payload;
  }
  const prefetched = service.takePayload(MIN_VALIDITY_MS);
  if (prefetched) {
    return prefetched;
  }
  try {
    return await service.solveForRequest();
  } catch (error) {
    if ((error as CaptchaError).kind === 'not-found') {
      return null;
    }
    throw error;
  }
}

function findRule(
  rules: readonly ProtectedRequest[],
  req: HttpRequest<unknown>,
): ProtectedRequest | undefined {
  return rules.find(
    (rule) =>
      rule.method.toUpperCase() === req.method.toUpperCase() &&
      (typeof rule.url === 'string'
        ? rule.url === req.url || rule.url === pathOf(req.url)
        : rule.url.test(req.url)),
  );
}

function withHeader(
  req: HttpRequest<unknown>,
  headerName: string,
  payload: string,
  retried: boolean,
): HttpRequest<unknown> {
  const context = new HttpContext();
  for (const key of req.context.keys()) {
    context.set(key, req.context.get(key));
  }
  context.set(CAPTCHA_RETRIED, retried);
  return req.clone({ setHeaders: { [headerName]: payload }, context });
}
