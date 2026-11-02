import { InjectionToken } from '@angular/core';

/** Modo de verificación. */
export type CaptchaMode = 'checkbox' | 'invisible';

/** Estado observable de la verificación. */
export type CaptchaState = 'idle' | 'verifying' | 'verified' | 'expired' | 'blocked' | 'error';

/** Textos del componente; `blocked` admite `{minutes}`. */
export interface CaptchaTexts {
  checkboxLabel: string;
  verifying: string;
  verified: string;
  error: string;
  retry: string;
  expired: string;
  blocked: string;
  unsupported: string;
  invisibleNotice: string;
  privacyLink: string;
  newWindow: string;
}

/** Petición protegida; `mode` sobrescribe el modo global. */
export interface ProtectedRequest {
  method: string;
  url: string | RegExp;
  mode?: CaptchaMode;
}

/** Configuración pública de la librería. */
export interface CaptchaConfig {
  mode: CaptchaMode;
  challengeUrl: string;
  protectedRequests?: readonly ProtectedRequest[];
  headerName?: string;
  solveOn?: 'load' | 'submit';
  solveTimeoutMs?: number;
  workerFactory?: () => Worker;
  language?: 'es' | 'en';
  texts?: Partial<CaptchaTexts>;
  /** Opcional. Si se define, `<captcha-notice>` muestra el enlace a la política de privacidad. */
  privacyPolicyUrl?: string;
}

/** Configuración con todos los valores por defecto aplicados. */
export interface ResolvedCaptchaConfig {
  mode: CaptchaMode;
  challengeUrl: string;
  protectedRequests: readonly ProtectedRequest[];
  headerName: string;
  solveOn: 'load' | 'submit';
  solveTimeoutMs: number;
  workerFactory?: () => Worker;
  language: 'es' | 'en';
  texts: CaptchaTexts;
  privacyPolicyUrl?: string;
}

/** Error tipado de la verificación. */
export interface CaptchaError {
  kind:
    | 'required'
    | 'invalid'
    | 'expired'
    | 'blocked'
    | 'unavailable'
    | 'timeout'
    | 'unsupported'
    | 'not-found';
  retryAfterSeconds?: number;
}

export function isCaptchaError(value: unknown): value is CaptchaError {
  return typeof value === 'object' && value !== null && 'kind' in value;
}

export const CAPTCHA_CONFIG = new InjectionToken<ResolvedCaptchaConfig>('CAPTCHA_CONFIG');
