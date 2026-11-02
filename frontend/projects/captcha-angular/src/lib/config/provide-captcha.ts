import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { resolveTexts } from '../i18n/resolve-texts';
import { CAPTCHA_CONFIG, CaptchaConfig, ResolvedCaptchaConfig } from './captcha-config';

const HEADER_TOKEN = /^[A-Za-z0-9-]{1,64}$/;

export const DEFAULT_CAPTCHA_CONFIG: Omit<ResolvedCaptchaConfig, 'texts'> = {
  mode: 'checkbox',
  challengeUrl: '/api/auth/challenge',
  protectedRequests: [{ method: 'POST', url: '/api/auth/login' }],
  headerName: 'X-Altcha',
  solveOn: 'load',
  solveTimeoutMs: 30000,
  language: 'es',
};

/** Aplica valores por defecto y valida la configuración; lanza un error descriptivo. */
export function resolveCaptchaConfig(config: Partial<CaptchaConfig> = {}): ResolvedCaptchaConfig {
  const resolved: ResolvedCaptchaConfig = {
    ...DEFAULT_CAPTCHA_CONFIG,
    ...stripUndefined(config),
    texts: resolveTexts(config.language ?? 'es', config.texts),
  };
  if (!resolved.challengeUrl) {
    throw new Error('[captcha] CaptchaConfig.challengeUrl no puede estar vacío');
  }
  if (!HEADER_TOKEN.test(resolved.headerName)) {
    throw new Error('[captcha] CaptchaConfig.headerName debe ser un token HTTP válido');
  }
  if (!(resolved.solveTimeoutMs > 0)) {
    throw new Error('[captcha] CaptchaConfig.solveTimeoutMs debe ser mayor que 0');
  }
  return resolved;
}

/** Registra la librería: `providers: [provideCaptcha({ mode: 'checkbox' })]`. */
export function provideCaptcha(config: Partial<CaptchaConfig> = {}): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: CAPTCHA_CONFIG, useValue: resolveCaptchaConfig(config) },
  ]);
}

function stripUndefined<T extends object>(value: T): Partial<T> {
  return Object.fromEntries(Object.entries(value).filter(([, v]) => v !== undefined)) as Partial<T>;
}
