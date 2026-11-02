import { Page, expect } from '@playwright/test';

let counter = 0;

/** IP simulada única por prueba (X-Forwarded-For; solo perfil e2e) para aislar los bloqueos. */
export function uniqueIp(): string {
  counter++;
  return `198.51.${Math.floor(Math.random() * 200) + 1}.${(counter % 250) + 1}`;
}

export const USER = 'demo';
export const PASSWORD = () => process.env['SAMPLE_USER_PASSWORD']!;

export async function useIp(page: Page, ip = uniqueIp()): Promise<string> {
  await page.setExtraHTTPHeaders({ 'X-Forwarded-For': ip });
  return ip;
}

export async function fillCredentials(
  page: Page,
  password = PASSWORD(),
  username = USER,
): Promise<void> {
  await page.getByLabel('Usuario').fill(username);
  await page.getByLabel('Contraseña').fill(password);
}

export async function checkNotARobot(page: Page): Promise<void> {
  await page.getByLabel('No soy un robot').check();
  await expect(page.getByRole('status').filter({ hasText: 'Verificado' })).toBeVisible();
}

export const message = (page: Page) => page.getByTestId('message');
