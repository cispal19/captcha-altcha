import { solveChallenge } from 'altcha-lib';
import type { Challenge, DeriveKeyFunction, Solution } from 'altcha-lib';
import { deriveKey as pbkdf2 } from 'altcha-lib/algorithms/web/pbkdf2';
import { deriveKey as sha } from 'altcha-lib/algorithms/web/sha';

/** Mensaje aceptado por el worker: es el único comando expuesto. */
export interface SolveRequest {
  type: 'solve';
  challenge: Challenge;
  timeout: number;
}

export type SolveResponse = { solution: Solution } | { error: 'timeout' | 'unsupported' };

/** Función de derivación de navegador (WebCrypto) para el algoritmo del desafío. */
export function deriveKeyFor(algorithm: string): DeriveKeyFunction | null {
  if (algorithm.startsWith('PBKDF2/')) {
    return pbkdf2;
  }
  if (algorithm.startsWith('SHA-')) {
    return sha;
  }
  return null;
}

export function webCryptoAvailable(): boolean {
  return typeof globalThis.crypto?.subtle?.deriveBits === 'function';
}

/** Resuelve la prueba de trabajo; `null` si vence el tiempo máximo. */
export async function solvePow(challenge: Challenge, timeout: number): Promise<SolveResponse> {
  const deriveKey = deriveKeyFor(challenge.parameters.algorithm);
  if (!deriveKey || !webCryptoAvailable()) {
    return { error: 'unsupported' };
  }
  const solution = await solveChallenge({ challenge, deriveKey, timeout });
  return solution ? { solution } : { error: 'timeout' };
}

/** Manejador de mensajes del worker; cualquier otro comando se rechaza. */
export async function handleSolveMessage(data: unknown): Promise<SolveResponse> {
  const request = data as Partial<SolveRequest> | null;
  if (!request || request.type !== 'solve' || !request.challenge?.parameters) {
    return { error: 'unsupported' };
  }
  return solvePow(request.challenge, request.timeout ?? 30000);
}

interface WorkerScope {
  addEventListener(type: 'message', listener: (event: MessageEvent) => void): void;
  postMessage(message: SolveResponse): void;
}

/**
 * Instala el manejador en el worker. Archivo del proyecto consumidor (`captcha.worker.ts`):
 * `import { installCaptchaWorker } from '@cispal19/captcha-angular/worker'; installCaptchaWorker();`
 */
export function installCaptchaWorker(scope: WorkerScope = self as unknown as WorkerScope): void {
  scope.addEventListener('message', (event: MessageEvent) => {
    void handleSolveMessage(event.data).then((response) => scope.postMessage(response));
  });
}
