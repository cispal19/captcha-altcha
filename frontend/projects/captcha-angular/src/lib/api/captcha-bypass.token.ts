import { HttpContextToken } from '@angular/common/http';

/** Marca peticiones que el interceptor no debe procesar (la propia petición del desafío). */
export const CAPTCHA_BYPASS = new HttpContextToken<boolean>(() => false);

/** Marca una petición ya reintentada tras un 403 para no entrar en bucle. */
export const CAPTCHA_RETRIED = new HttpContextToken<boolean>(() => false);
