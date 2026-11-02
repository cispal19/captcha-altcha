import { InjectionToken, inject } from '@angular/core';
import type { Challenge, Solution } from 'altcha-lib';
import { solvePow, webCryptoAvailable } from '@cispal19/captcha-angular/worker';
import type { SolveResponse } from '@cispal19/captcha-angular/worker';
import { CAPTCHA_CONFIG, CaptchaError } from '../config/captcha-config';

/** Resuelve la prueba de trabajo de un desafío. */
export interface PowSolver {
  solve(challenge: Challenge, timeoutMs: number): Promise<Solution>;
}

/**
 * Implementación por defecto: en un Web Worker si hay `workerFactory`; si no, en el hilo
 * principal con WebCrypto asíncrono, que no bloquea la interfaz.
 */
export class AltchaPowSolver implements PowSolver {
  constructor(private readonly workerFactory?: () => Worker) {}

  async solve(challenge: Challenge, timeoutMs: number): Promise<Solution> {
    if (!webCryptoAvailable()) {
      throw { kind: 'unsupported' } satisfies CaptchaError;
    }
    const response = this.workerFactory
      ? await this.solveInWorker(challenge, timeoutMs)
      : await solvePow(challenge, timeoutMs);
    if ('solution' in response) {
      return response.solution;
    }
    throw { kind: response.error } satisfies CaptchaError;
  }

  private solveInWorker(challenge: Challenge, timeoutMs: number): Promise<SolveResponse> {
    return new Promise((resolve) => {
      const worker = this.workerFactory!();
      const finish = (response: SolveResponse) => {
        clearTimeout(timer);
        worker.terminate();
        resolve(response);
      };
      const timer = setTimeout(() => finish({ error: 'timeout' }), timeoutMs + 1000);
      worker.onmessage = (event: MessageEvent<SolveResponse>) => finish(event.data);
      worker.onerror = () => finish({ error: 'unsupported' });
      worker.postMessage({ type: 'solve', challenge, timeout: timeoutMs });
    });
  }
}

export const POW_SOLVER = new InjectionToken<PowSolver>('POW_SOLVER', {
  providedIn: 'root',
  factory: () => new AltchaPowSolver(inject(CAPTCHA_CONFIG).workerFactory),
});
