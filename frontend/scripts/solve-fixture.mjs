// Resuelve fixtures/altcha/challenge.json con altcha-lib (WebCrypto)
// y escribe fixtures/altcha/payload.b64 para AltchaInteropTest (Java).
// Uso: node frontend/scripts/solve-fixture.mjs
import { solveChallenge } from 'altcha-lib';
import { deriveKey } from 'altcha-lib/algorithms/web/pbkdf2';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const fixtures = resolve(dirname(fileURLToPath(import.meta.url)), '../../fixtures/altcha');
const challenge = JSON.parse(readFileSync(resolve(fixtures, 'challenge.json'), 'utf8'));
const started = performance.now();
const solution = await solveChallenge({ challenge, deriveKey });
if (!solution) {
  throw new Error('No se pudo resolver el desafío del fixture');
}
const elapsed = Math.round(performance.now() - started);
writeFileSync(
  resolve(fixtures, 'payload.b64'),
  Buffer.from(JSON.stringify({ challenge, solution })).toString('base64') + '\n',
);
writeFileSync(
  resolve(fixtures, 'solution.json'),
  JSON.stringify({ counter: solution.counter, derivedKey: solution.derivedKey }, null, 2) + '\n',
);
console.log(`Resuelto en ${elapsed} ms (counter=${solution.counter}) → payload.b64`);
