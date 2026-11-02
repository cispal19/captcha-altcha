import { expect, test } from '@playwright/test';
import {
  PASSWORD,
  USER,
  checkNotARobot,
  fillCredentials,
  message,
  uniqueIp,
  useIp,
} from './support';

test.describe('modo casilla', () => {
  test('V1: marcar la casilla y entrar → 200 en menos de 1 s de verificación', async ({ page }) => {
    await useIp(page);
    await page.goto('/login');
    await fillCredentials(page);
    const started = Date.now();
    await checkNotARobot(page);
    expect(Date.now() - started).toBeLessThan(3000);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(message(page)).toHaveText('Ingreso correcto');
  });

  test('V3: sin cabecera → 403 CAPTCHA_REQUIRED sin traza', async ({ request }) => {
    const res = await request.post('/api/auth/login', {
      data: { username: USER, password: PASSWORD() },
      headers: { 'X-Forwarded-For': uniqueIp() },
    });
    expect(res.status()).toBe(403);
    const body = await res.json();
    expect(body.code).toBe('CAPTCHA_REQUIRED');
    expect(JSON.stringify(body)).not.toMatch(/Exception|\tat /);
  });

  test('V4: reutilizar la cabecera capturada → 403 CAPTCHA_INVALID', async ({ page, request }) => {
    const ip = await useIp(page);
    await page.goto('/login');
    await fillCredentials(page);
    await checkNotARobot(page);
    const [login] = await Promise.all([
      page.waitForRequest((r) => r.url().endsWith('/api/auth/login')),
      page.getByRole('button', { name: 'Ingresar' }).click(),
    ]);
    await expect(message(page)).toHaveText('Ingreso correcto');
    const header = (await login.allHeaders())['x-altcha'];
    expect(header).toBeTruthy();
    const replay = await request.post('/api/auth/login', {
      data: { username: USER, password: PASSWORD() },
      headers: { 'X-Altcha': header, 'X-Forwarded-For': ip },
    });
    expect(replay.status()).toBe(403);
    expect((await replay.json()).code).toBe('CAPTCHA_INVALID');
  });

  test('V5: la casilla expira (TTL 10 s), se vuelve a marcar y el login continúa', async ({
    page,
  }) => {
    await useIp(page);
    await page.goto('/login');
    await fillCredentials(page);
    await checkNotARobot(page);
    await expect(
      page.getByRole('status').filter({ hasText: 'La verificación expiró' }),
    ).toBeVisible({ timeout: 15_000 });
    await expect(page.getByLabel('No soy un robot')).not.toBeChecked();
    await checkNotARobot(page);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(message(page)).toHaveText('Ingreso correcto');
  });

  test('sin marcar la casilla el formulario no se envía', async ({ page }) => {
    await useIp(page);
    await page.goto('/login');
    await fillCredentials(page);
    const requests: string[] = [];
    page.on('request', (r) => requests.push(r.url()));
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(message(page)).toContainText('marque la casilla');
    expect(requests.some((u) => u.endsWith('/api/auth/login'))).toBe(false);
  });
});
