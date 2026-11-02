import { defineConfig, devices } from '@playwright/test';
import { randomBytes } from 'node:crypto';

/**
 * E2E: backend de ejemplo (perfil e2e, :8080) y uno con ruta/cabecera cambiadas (perfil
 * e2e-paths, :8081), cada uno con su frontend (:4200 y :4201). Requiere Redis en localhost:6379
 * (docker compose up -d redis) y el starter instalado (mvn -f ../backend/pom.xml install -DskipTests).
 */
process.env['ALTCHA_HMAC_KEY'] ??= randomBytes(48).toString('hex');
process.env['SAMPLE_USER_PASSWORD'] ??= randomBytes(12).toString('hex');

const backend = (profiles: string, port: number, readyPath: string) => ({
  command: `mvn -q -f ../backend/pom.xml -pl captcha-sample-app spring-boot:run -Dspring-boot.run.profiles=${profiles} -Dspring-boot.run.arguments=--server.port=${port}`,
  url: `http://localhost:${port}${readyPath}`,
  timeout: 180_000,
  reuseExistingServer: !process.env['CI'],
  env: {
    ALTCHA_HMAC_KEY: process.env['ALTCHA_HMAC_KEY']!,
    SAMPLE_USER_PASSWORD: process.env['SAMPLE_USER_PASSWORD']!,
  },
});

const frontend = (port: number, backendPort: number) => ({
  command: `npx ng serve captcha-sample --port ${port} --proxy-config projects/captcha-sample/proxy.conf.mjs`,
  url: `http://localhost:${port}/login`,
  timeout: 180_000,
  reuseExistingServer: !process.env['CI'],
  env: { BACKEND_URL: `http://localhost:${backendPort}` },
});

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    backend('e2e', 8080, '/api/auth/challenge'),
    backend('e2e,e2e-paths', 8081, '/api/seguridad/desafio'),
    frontend(4200, 8080),
    frontend(4201, 8081),
  ],
});
