import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Challenge } from 'altcha-lib';
import { firstValueFrom } from 'rxjs';
import { CAPTCHA_CONFIG, CaptchaError } from '../config/captcha-config';
import { CAPTCHA_BYPASS } from './captcha-bypass.token';

/** Obtiene desafíos del backend (`GET challengeUrl`). Uso interno. */
@Injectable({ providedIn: 'root' })
export class ChallengeApiClient {
  private readonly http = inject(HttpClient);
  private readonly config = inject(CAPTCHA_CONFIG);

  async fetch(url: string = this.config.challengeUrl): Promise<Challenge> {
    try {
      return await firstValueFrom(
        this.http.get<Challenge>(url, { context: new HttpContext().set(CAPTCHA_BYPASS, true) }),
      );
    } catch (error) {
      throw toCaptchaError(error, url);
    }
  }
}

/** Traduce errores HTTP a {@link CaptchaError}; ante un 404 escribe un diagnóstico en consola. */
export function toCaptchaError(error: unknown, url?: string): CaptchaError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 429) {
      return { kind: 'blocked', retryAfterSeconds: retryAfterSeconds(error) };
    }
    if (error.status === 404 && url) {
      console.error(
        `[captcha] No se encontró el endpoint de desafío en "${url}". Verifique que ` +
          'CaptchaConfig.challengeUrl coincida con security.altcha.challenge-path del backend.',
      );
      return { kind: 'not-found' };
    }
  }
  return { kind: 'unavailable' };
}

export function retryAfterSeconds(error: HttpErrorResponse): number | undefined {
  const value = Number(error.headers?.get('Retry-After'));
  return Number.isFinite(value) && value > 0 ? value : undefined;
}
