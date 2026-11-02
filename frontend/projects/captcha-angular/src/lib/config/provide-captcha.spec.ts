import { TestBed } from '@angular/core/testing';
import { CAPTCHA_CONFIG } from './captcha-config';
import { provideCaptcha, resolveCaptchaConfig } from './provide-captcha';

describe('provideCaptcha', () => {
  it('aplica los valores por defecto del contrato', () => {
    TestBed.configureTestingModule({ providers: [provideCaptcha({})] });
    const config = TestBed.inject(CAPTCHA_CONFIG);
    expect(config.mode).toBe('checkbox');
    expect(config.challengeUrl).toBe('/api/auth/challenge');
    expect(config.protectedRequests).toEqual([{ method: 'POST', url: '/api/auth/login' }]);
    expect(config.headerName).toBe('X-Altcha');
    expect(config.language).toBe('es');
    expect(config.solveOn).toBe('load');
    expect(config.solveTimeoutMs).toBe(30000);
    expect(config.texts.checkboxLabel).toBe('No soy un robot');
  });

  it('acepta mode por entrada de protectedRequests', () => {
    const config = resolveCaptchaConfig({
      protectedRequests: [{ method: 'POST', url: '/api/x', mode: 'checkbox' }],
    });
    expect(config.protectedRequests[0].mode).toBe('checkbox');
  });

  it('rechaza headerName inválido, timeout no positivo y challengeUrl vacío', () => {
    expect(() => resolveCaptchaConfig({ headerName: 'X Altcha' })).toThrow(/headerName/);
    expect(() => resolveCaptchaConfig({ solveTimeoutMs: 0 })).toThrow(/solveTimeoutMs/);
    expect(() => resolveCaptchaConfig({ challengeUrl: '' })).toThrow(/challengeUrl/);
  });

  describe('modo invisible', () => {
    it('privacyPolicyUrl es opcional', () => {
      expect(resolveCaptchaConfig({ mode: 'invisible' }).privacyPolicyUrl).toBeUndefined();
      expect(() =>
        resolveCaptchaConfig({
          protectedRequests: [{ method: 'POST', url: '/x', mode: 'invisible' }],
        }),
      ).not.toThrow();
    });

    it('con privacyPolicyUrl es válido', () => {
      expect(
        resolveCaptchaConfig({ mode: 'invisible', privacyPolicyUrl: '/privacidad' }).mode,
      ).toBe('invisible');
    });
  });
});
