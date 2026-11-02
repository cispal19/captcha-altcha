import challenge from '../../../../../../fixtures/altcha/challenge.json';
import expected from '../../../../../../fixtures/altcha/solution.json';
import type { Challenge } from 'altcha-lib';
import { handleSolveMessage } from '@cispal19/captcha-angular/worker';
import { AltchaPowSolver } from './pow-solver';

const fixture = challenge as Challenge;

describe('AltchaPowSolver', () => {
  it('S-1: resuelve en el hilo principal el desafío creado en Java', async () => {
    const solution = await new AltchaPowSolver().solve(fixture, 30000);
    expect(solution.counter).toBe(expected.counter);
    expect(solution.derivedKey).toBe(expected.derivedKey);
  });

  it('timeout configurable → CaptchaError timeout', async () => {
    const hard = { ...fixture, parameters: { ...fixture.parameters, keyPrefix: 'ffffffff' } };
    await expect(new AltchaPowSolver().solve(hard, 50)).rejects.toEqual({ kind: 'timeout' });
  });

  it('E1: sin crypto.subtle (contexto no seguro) → unsupported sin resolver', async () => {
    const original = globalThis.crypto;
    Object.defineProperty(globalThis, 'crypto', { value: {}, configurable: true });
    try {
      await expect(new AltchaPowSolver().solve(fixture, 1000)).rejects.toEqual({
        kind: 'unsupported',
      });
    } finally {
      Object.defineProperty(globalThis, 'crypto', { value: original, configurable: true });
    }
  });

  it('con workerFactory resuelve vía postMessage y termina el worker', async () => {
    const terminate = vi.fn();
    const fakeWorker = {
      onmessage: null as ((e: MessageEvent) => void) | null,
      onerror: null,
      terminate,
      postMessage(data: unknown) {
        void handleSolveMessage(data).then((r) => this.onmessage?.({ data: r } as MessageEvent));
      },
    };
    const solver = new AltchaPowSolver(() => fakeWorker as unknown as Worker);
    const solution = await solver.solve(fixture, 30000);
    expect(solution.counter).toBe(expected.counter);
    expect(terminate).toHaveBeenCalled();
  });

  it('worker que falla → unsupported', async () => {
    const fakeWorker = {
      onmessage: null,
      onerror: null as (() => void) | null,
      terminate: vi.fn(),
      postMessage() {
        this.onerror?.();
      },
    };
    await expect(
      new AltchaPowSolver(() => fakeWorker as unknown as Worker).solve(fixture, 1000),
    ).rejects.toEqual({ kind: 'unsupported' });
  });
});
