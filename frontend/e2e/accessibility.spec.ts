import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { PASSWORD, USER, message, useIp } from './support';

test.describe('accesibilidad WCAG 2.1 AA', () => {
  for (const mode of ['checkbox', 'invisible']) {
    test(`axe sin violaciones críticas ni serias en modo ${mode}`, async ({ page }) => {
      await useIp(page);
      await page.goto(`/login${mode === 'invisible' ? '?mode=invisible' : ''}`);
      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      const serious = results.violations.filter((v) =>
        ['critical', 'serious'].includes(v.impact ?? ''),
      );
      expect(serious.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
    });
  }

  test('flujo completo solo con teclado', async ({ page }) => {
    await useIp(page);
    await page.goto('/login');
    await page.getByLabel('Usuario').focus();
    await page.keyboard.type(USER);
    await page.keyboard.press('Tab');
    await page.keyboard.type(PASSWORD());
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('No soy un robot')).toBeFocused();
    await page.keyboard.press('Space');
    await expect(page.getByRole('status').filter({ hasText: 'Verificado' })).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Ingresar' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(message(page)).toHaveText('Ingreso correcto');
  });
});
