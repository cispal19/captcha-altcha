// Genera perf/payloads.json para la prueba de carga k6: N desafíos reales resueltos de antemano.
// Uso: node frontend/scripts/presolve.mjs [N=500] [BASE_URL=http://localhost:8080]
import { solveChallenge } from 'altcha-lib';
import { deriveKey } from 'altcha-lib/algorithms/web/pbkdf2';
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const count = Number(process.argv[2] ?? 500);
const baseUrl = process.argv[3] ?? 'http://localhost:8080';
const out = [];
for (let i = 0; i < count; i++) {
  const ip = `10.200.${Math.floor(i / 250)}.${(i % 250) + 1}`;
  const challenge = await (await fetch(`${baseUrl}/api/auth/challenge`, { headers: { 'X-Forwarded-For': ip } })).json();
  const solution = await solveChallenge({ challenge, deriveKey });
  out.push({ ip, header: Buffer.from(JSON.stringify({ challenge, solution })).toString('base64') });
}
const target = resolve(dirname(fileURLToPath(import.meta.url)), '../../perf/payloads.json');
writeFileSync(target, JSON.stringify(out));
console.log(`${count} payloads → ${target}`);
