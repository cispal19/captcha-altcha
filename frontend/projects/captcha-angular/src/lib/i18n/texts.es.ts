import { CaptchaTexts } from '../config/captcha-config';

/** Textos por defecto en español. */
export const TEXTS_ES: CaptchaTexts = {
  checkboxLabel: 'No soy un robot',
  verifying: 'Verificando…',
  verified: 'Verificado',
  error: 'No se pudo verificar. Reintentar',
  retry: 'Reintentar',
  expired: 'La verificación expiró. Vuelva a marcar la casilla',
  blocked: 'Demasiados intentos. Intente en {minutes} min',
  unsupported:
    'Su navegador no permite completar la verificación. Use una conexión segura (HTTPS) o un navegador actualizado',
  invisibleNotice: 'Este formulario está protegido contra bots.',
  privacyLink: 'Política de privacidad',
  newWindow: '(se abre en una ventana nueva)',
};
