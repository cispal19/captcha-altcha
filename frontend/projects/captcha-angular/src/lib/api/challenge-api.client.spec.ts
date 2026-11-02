import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideCaptcha } from '../config/provide-captcha';
import { CAPTCHA_BYPASS } from './captcha-bypass.token';
import { ChallengeApiClient } from './challenge-api.client';

describe('ChallengeApiClient', () => {
  let client: ChallengeApiClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideCaptcha({})],
    });
    client = TestBed.inject(ChallengeApiClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GET challengeUrl con CAPTCHA_BYPASS → desafío', async () => {
    const promise = client.fetch();
    const req = http.expectOne('/api/auth/challenge');
    expect(req.request.method).toBe('GET');
    expect(req.request.context.get(CAPTCHA_BYPASS)).toBe(true);
    req.flush({ parameters: { algorithm: 'PBKDF2/SHA-256' }, signature: 'ab' });
    await expect(promise).resolves.toEqual({
      parameters: { algorithm: 'PBKDF2/SHA-256' },
      signature: 'ab',
    });
  });

  it('acepta una URL explícita (la que indica un 403)', async () => {
    const promise = client.fetch('/api/seguridad/desafio');
    http.expectOne('/api/seguridad/desafio').flush({ parameters: {}, signature: 'x' });
    await expect(promise).resolves.toBeTruthy();
  });

  it('429 → blocked con retryAfterSeconds de Retry-After', async () => {
    const promise = client.fetch();
    http
      .expectOne('/api/auth/challenge')
      .flush(
        { code: 'TOO_MANY_ATTEMPTS' },
        { status: 429, statusText: 'Too Many', headers: new HttpHeaders({ 'Retry-After': '120' }) },
      );
    await expect(promise).rejects.toEqual({ kind: 'blocked', retryAfterSeconds: 120 });
  });

  it('503 y error de red → unavailable', async () => {
    const p1 = client.fetch();
    http.expectOne('/api/auth/challenge').flush({}, { status: 503, statusText: 'Down' });
    await expect(p1).rejects.toEqual({ kind: 'unavailable' });
    const p2 = client.fetch();
    http.expectOne('/api/auth/challenge').error(new ProgressEvent('error'));
    await expect(p2).rejects.toEqual({ kind: 'unavailable' });
  });

  it('404 → not-found y diagnóstico exacto en consola', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const promise = client.fetch();
    http.expectOne('/api/auth/challenge').flush({}, { status: 404, statusText: 'Not Found' });
    await expect(promise).rejects.toEqual({ kind: 'not-found' });
    expect(spy).toHaveBeenCalledWith(
      '[captcha] No se encontró el endpoint de desafío en "/api/auth/challenge". Verifique que ' +
        'CaptchaConfig.challengeUrl coincida con security.altcha.challenge-path del backend.',
    );
    spy.mockRestore();
  });
});
