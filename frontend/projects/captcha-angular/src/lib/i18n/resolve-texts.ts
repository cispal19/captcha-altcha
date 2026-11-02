import { CaptchaTexts } from '../config/captcha-config';
import { TEXTS_EN } from './texts.en';
import { TEXTS_ES } from './texts.es';

/** Combina el idioma con los textos reemplazados por el proyecto. */
export function resolveTexts(
  language: 'es' | 'en' = 'es',
  overrides: Partial<CaptchaTexts> = {},
): CaptchaTexts {
  return { ...(language === 'en' ? TEXTS_EN : TEXTS_ES), ...overrides };
}

/** Interpola `{clave}` en un texto. */
export function interpolate(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    key in values ? String(values[key]) : match,
  );
}
