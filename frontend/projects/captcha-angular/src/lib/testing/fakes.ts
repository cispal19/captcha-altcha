import type { Challenge, Solution } from 'altcha-lib';
import { PowSolver } from '../solver/pow-solver';

/** Desafío falso con vigencia configurable (segundos desde ahora). */
export function fakeChallenge(validSeconds = 120, signature = 'ab'): Challenge {
  return {
    parameters: {
      algorithm: 'PBKDF2/SHA-256',
      nonce: '00',
      salt: '11',
      cost: 1000,
      keyLength: 32,
      keyPrefix: '00',
      expiresAt: Math.floor(Date.now() / 1000) + validSeconds,
    },
    signature,
  };
}

/** Solver instantáneo para pruebas de servicio e interceptor. */
export class FakeSolver implements PowSolver {
  calls = 0;
  async solve(): Promise<Solution> {
    this.calls++;
    return { counter: this.calls, derivedKey: '00'.repeat(32) };
  }
}
