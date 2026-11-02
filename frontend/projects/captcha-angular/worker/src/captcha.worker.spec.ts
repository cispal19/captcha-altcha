import challenge from '../../../../../fixtures/altcha/challenge.json';
import expected from '../../../../../fixtures/altcha/solution.json';
import type { Challenge } from 'altcha-lib';
import { deriveKeyFor, handleSolveMessage, installCaptchaWorker } from './solve';

describe('manejador del worker', () => {
  it('resuelve el fixture creado en Java con el mismo contador', async () => {
    const response = await handleSolveMessage({
      type: 'solve',
      challenge: challenge as Challenge,
      timeout: 30000,
    });
    expect(response).toEqual({
      solution: expect.objectContaining({
        counter: expected.counter,
        derivedKey: expected.derivedKey,
      }),
    });
  });

  it('no expone ningún otro comando', async () => {
    expect(await handleSolveMessage({ type: 'eval', code: '1' })).toEqual({ error: 'unsupported' });
    expect(await handleSolveMessage(null)).toEqual({ error: 'unsupported' });
  });

  it('algoritmo desconocido → unsupported', async () => {
    const bad = {
      ...(challenge as Challenge),
      parameters: { ...(challenge as Challenge).parameters, algorithm: 'MD5' },
    };
    expect(await handleSolveMessage({ type: 'solve', challenge: bad, timeout: 100 })).toEqual({
      error: 'unsupported',
    });
    expect(deriveKeyFor('SHA-256')).toBeTypeOf('function');
  });

  it('installCaptchaWorker responde por postMessage', async () => {
    let listener: ((e: MessageEvent) => void) | undefined;
    const posted = new Promise((resolve) => {
      installCaptchaWorker({
        addEventListener: (_t, l) => (listener = l),
        postMessage: resolve,
      });
    });
    listener?.({ data: { type: 'x' } } as MessageEvent);
    expect(await posted).toEqual({ error: 'unsupported' });
  });
});
