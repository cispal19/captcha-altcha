import { expect, test } from '@playwright/test';
import { fillCredentials, message, useIp } from './support';

test.describe('modo invisible', () => {
  test('V2: sin casilla, con aviso y enlace de privacidad; login 200 con X-Altcha', async ({
    page,
  }) => {
    await useIp(page);
    await page.goto('/login?mode=invisible');
    await expect(page.getByLabel('No soy un robot')).toHaveCount(0);
    await expect(page.getByText('Este formulario está protegido contra bots.')).toBeVisible();
    await expect(page.getByRole('link', { name: /Política de privacidad/ })).toHaveAttribute(
      'href',
      '/privacidad.html',
    );
    await fillCredentials(page);
    const [login] = await Promise.all([
      page.waitForRequest((r) => r.url().endsWith('/api/auth/login')),
      page.getByRole('button', { name: 'Ingresar' }).click(),
    ]);
    expect((await login.allHeaders())['x-altcha']).toBeTruthy();
    await expect(message(page)).toHaveText('Ingreso correcto');
  });

  test('V16: el desafío se pide al cargar, antes de pulsar Ingresar', async ({ page }) => {
    await useIp(page);
    const challenge = page.waitForResponse((r) => r.url().endsWith('/api/auth/challenge'));
    await page.goto('/login?mode=invisible');
    expect((await challenge).status()).toBe(200);
    await fillCredentials(page);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(message(page)).toHaveText('Ingreso correcto');
  });

  test('V5 (invisible): solución anticipada expirada se renueva sola al enviar', async ({
    page,
  }) => {
    await useIp(page);
    await page.goto('/login?mode=invisible');
    await fillCredentials(page);
    await page.waitForTimeout(11_000);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(message(page)).toHaveText('Ingreso correcto');
  });

  test('desafío no disponible (503) → el login no se envía y se informa', async ({ page }) => {
    await useIp(page);
    await page.route('**/api/auth/challenge', (route) =>
      route.fulfill({ status: 503, body: '{}' }),
    );
    await page.goto('/login?mode=invisible');
    await fillCredentials(page);
    const requests: string[] = [];
    page.on('request', (r) => requests.push(r.url()));
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(message(page)).toHaveText('No se pudo verificar. Reintentar');
    expect(requests.some((u) => u.endsWith('/api/auth/login'))).toBe(false);
  });

  test('la consulta pública usa modo invisible mientras el login usa casilla', async ({ page }) => {
    await useIp(page);
    await page.goto('/consulta');
    await page.getByLabel('Número de documento').fill('12345678');
    await page.getByRole('button', { name: 'Consultar' }).click();
    await expect(message(page)).toHaveText('Consulta registrada');
  });

  test('V15: ruta y cabecera cambiadas solo en el backend; el frontend por defecto funciona', async ({
    page,
  }) => {
    await useIp(page);
    await page.goto('http://localhost:4201/login?mode=invisible');
    await fillCredentials(page);
    const retried = page.waitForRequest(
      (r) => r.url().endsWith('/api/auth/login') && !!r.headers()['x-captcha'],
    );
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await retried;
    await expect(message(page)).toHaveText('Ingreso correcto');
  });

  test('V15 (casilla): diagnóstico en consola si la ruta del desafío no coincide', async ({
    page,
  }) => {
    await useIp(page);
    const errors: string[] = [];
    page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
    await page.goto('http://localhost:4201/login');
    await page.getByLabel('No soy un robot').check();
    await expect
      .poll(() => errors.join('\n'))
      .toContain('[captcha] No se encontró el endpoint de desafío');
  });
});
