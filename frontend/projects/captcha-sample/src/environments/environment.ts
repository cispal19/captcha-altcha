import type { CaptchaMode } from '@cispal19/captcha-angular';

/**
 * Configuración del ejemplo. El modo también puede elegirse con `?mode=invisible` en la URL (lo
 * usan las pruebas E2E para cubrir ambos modos sin recompilar).
 */
export const environment = {
  captchaMode: 'checkbox' as CaptchaMode,
  privacyPolicyUrl: '/privacidad.html',
};
