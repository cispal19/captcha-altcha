import { expect, test } from '@playwright/test';
import { checkNotARobot, fillCredentials, message, useIp } from './support';

// Usuario distinto de "demo" para no bloquear a las demás pruebas; el bloqueo por IP aplica igual.
const ATTACKED_USER = 'usuario-atacado';

test('5 contraseñas incorrectas → el 6.º intento responde 429 con Retry-After ≈ 300', async ({
  page,
}) => {
  await useIp(page);
  await page.goto('/login');
  for (let i = 0; i < 5; i++) {
    await fillCredentials(page, 'incorrecta', ATTACKED_USER);
    await checkNotARobot(page);
    const [attempt] = await Promise.all([
      page.waitForResponse((r) => r.url().endsWith('/api/auth/login')),
      page.getByRole('button', { name: 'Ingresar' }).click(),
    ]);
    expect(attempt.status()).toBe(401);
  }
  await checkNotARobot(page);
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/api/auth/login')),
    page.getByRole('button', { name: 'Ingresar' }).click(),
  ]);
  expect(response.status()).toBe(429);
  const retryAfter = Number(response.headers()['retry-after']);
  expect(retryAfter).toBeGreaterThan(290);
  expect(retryAfter).toBeLessThanOrEqual(300);
  await expect(message(page)).toHaveText('Demasiados intentos. Intente más tarde.');
  await expect(
    page.getByRole('status').filter({ hasText: 'Demasiados intentos. Intente en 5 min' }),
  ).toBeVisible();
});
