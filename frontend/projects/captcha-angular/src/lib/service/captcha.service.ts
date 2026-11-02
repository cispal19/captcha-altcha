import { Injectable, inject, signal } from '@angular/core';
import type { Challenge, Solution } from 'altcha-lib';
import { ChallengeApiClient } from '../api/challenge-api.client';
import {
  CAPTCHA_CONFIG,
  CaptchaError,
  CaptchaState,
  isCaptchaError,
} from '../config/captcha-config';
import { POW_SOLVER } from '../solver/pow-solver';

interface CurrentPayload {
  payload: string;
  expiresAtMs: number;
}

/**
 * Obtiene y resuelve desafíos y guarda la solución vigente (de un solo uso). Su `state` es la
 * fuente de eventos en modo invisible.
 */
@Injectable({ providedIn: 'root' })
export class CaptchaService {
  private readonly config = inject(CAPTCHA_CONFIG);
  private readonly api = inject(ChallengeApiClient);
  private readonly solver = inject(POW_SOLVER);

  private readonly stateSignal = signal<CaptchaState>('idle');
  private readonly errorSignal = signal<CaptchaError | null>(null);
  readonly state = this.stateSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  private current: CurrentPayload | null = null;
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;
  private prefetching: Promise<unknown> | null = null;

  /** Obtiene y resuelve un desafío, lo guarda como solución vigente y devuelve el payload. */
  async solve(challengeUrl?: string): Promise<string> {
    this.clearCurrent();
    const result = await this.acquire(challengeUrl);
    this.current = result;
    this.scheduleExpiry(result.expiresAtMs);
    return result.payload;
  }

  /** Resuelve un desafío para una sola petición, sin guardarlo (lo usa el interceptor). */
  async solveForRequest(challengeUrl?: string): Promise<string> {
    const result = await this.acquire(challengeUrl);
    this.stateSignal.set('idle');
    return result.payload;
  }

  /**
   * Entrega la solución vigente una única vez. Devuelve `null` si no hay o si le quedan
   * `minValidityMs` o menos de vigencia.
   */
  takePayload(minValidityMs = 0): string | null {
    const current = this.current;
    if (!current) {
      return null;
    }
    this.clearCurrent();
    if (current.expiresAtMs - Date.now() <= minValidityMs) {
      return null;
    }
    this.stateSignal.set('idle');
    return current.payload;
  }

  /** Resolución anticipada (solveOn='load'); no hace nada si ya hay una solución o está en curso. */
  prefetch(): void {
    if (this.current || this.prefetching) {
      return;
    }
    this.prefetching = this.solve()
      .catch(() => undefined)
      .finally(() => (this.prefetching = null));
  }

  markBlocked(retryAfterSeconds?: number): void {
    this.clearCurrent();
    this.errorSignal.set({ kind: 'blocked', retryAfterSeconds });
    this.stateSignal.set('blocked');
  }

  reset(): void {
    this.clearCurrent();
    this.errorSignal.set(null);
    this.stateSignal.set('idle');
  }

  private async acquire(challengeUrl?: string): Promise<CurrentPayload> {
    this.errorSignal.set(null);
    this.stateSignal.set('verifying');
    try {
      const challenge = await this.api.fetch(challengeUrl);
      const solution = await this.solver.solve(challenge, this.config.solveTimeoutMs);
      this.stateSignal.set('verified');
      return {
        payload: encodePayload(challenge, solution),
        expiresAtMs: (challenge.parameters.expiresAt ?? Number.MAX_SAFE_INTEGER / 1000) * 1000,
      };
    } catch (error) {
      const captchaError: CaptchaError = isCaptchaError(error) ? error : { kind: 'unavailable' };
      this.errorSignal.set(captchaError);
      this.stateSignal.set(captchaError.kind === 'blocked' ? 'blocked' : 'error');
      throw captchaError;
    }
  }

  private scheduleExpiry(expiresAtMs: number): void {
    const delay = expiresAtMs - Date.now();
    if (delay > 2_147_000_000) {
      return;
    }
    this.expiryTimer = setTimeout(
      () => {
        this.current = null;
        this.stateSignal.set('expired');
      },
      Math.max(0, delay),
    );
  }

  private clearCurrent(): void {
    this.current = null;
    clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
  }
}

/** base64 de `{challenge, solution}`, tal como lo espera el backend. */
export function encodePayload(challenge: Challenge, solution: Solution): string {
  const payload = {
    challenge: { parameters: challenge.parameters, signature: challenge.signature },
    solution,
  };
  const bytes = new TextEncoder().encode(JSON.stringify(payload));
  let binary = '';
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary);
}
