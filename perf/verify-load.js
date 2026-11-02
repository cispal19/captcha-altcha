// k6: 500 usuarios virtuales concurrentes que piden un desafío, lo resuelven con
// payloads pre-resueltos y verifican contra la app de ejemplo (perfil e2e).
//
//   Backend: spring-boot:run con --server.forward-headers-strategy=framework (IP por X-Forwarded-For)
//   y vigencia normal (120 s), para que los payloads no expiren antes de la carga.
//   k6 run -e BASE_URL=http://localhost:8080 -e PAYLOADS=perf/payloads.json perf/verify-load.js
//
// perf/payloads.json se genera con `node frontend/scripts/presolve.mjs` (no versionado: cada
// payload es de un solo uso y expira).
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const payloads = new SharedArray('payloads', () => JSON.parse(open(__ENV.PAYLOADS || './payloads.json')));

export const options = {
  scenarios: {
    verify: { executor: 'shared-iterations', vus: 500, iterations: 500, maxDuration: '2m' },
    challenge: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 50,
      exec: 'challenge',
      startTime: '30s',
    },
  },
  thresholds: {
    'http_req_duration{scenario:verify}': ['p(95)<1000'],
    'http_req_duration{endpoint:challenge}': ['p(95)<20'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const item = payloads[exec.scenario.iterationInTest % payloads.length];
  // Ruta protegida por la librería sin autenticación (mide solo la verificación anti-bot).
  const res = http.post(`${BASE_URL}/api/public/consulta`, JSON.stringify({ dni: '1' }), {
    headers: { 'Content-Type': 'application/json', 'X-Altcha': item.header, 'X-Forwarded-For': item.ip },
    tags: { endpoint: 'verify' },
  });
  check(res, { 'verificado (200)': (r) => r.status === 200 });
}

export function challenge() {
  const res = http.get(`${BASE_URL}/api/auth/challenge`, {
    headers: { 'X-Forwarded-For': `10.${exec.vu.idInTest % 250}.${Math.floor(exec.scenario.iterationInTest / 250) % 250}.${exec.scenario.iterationInTest % 250}` },
    tags: { endpoint: 'challenge' },
  });
  check(res, { 'desafío 200': (r) => r.status === 200 });
  sleep(0);
}
