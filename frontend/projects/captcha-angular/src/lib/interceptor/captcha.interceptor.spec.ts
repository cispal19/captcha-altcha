import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { CaptchaConfig } from '../config/captcha-config';
import { provideCaptcha } from '../config/provide-captcha';
import { CaptchaService } from '../service/captcha.service';
import { POW_SOLVER } from '../solver/pow-solver';
import { FakeSolver, fakeChallenge } from '../testing/fakes';
import { captchaInterceptor } from './captcha.interceptor';

describe('captchaInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let service: CaptchaService;

  function setup(config: Partial<CaptchaConfig> = {}) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([captchaInterceptor])),
        provideHttpClientTesting(),
        provideCaptcha(config),
        { provide: POW_SOLVER, useValue: new FakeSolver() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    service = TestBed.inject(CaptchaService);
  }

  async function verifiedCheckbox(): Promise<string> {
    const p = service.solve();
    await Promise.resolve();
    http.expectOne('/api/auth/challenge').flush(fakeChallenge());
    return p;
  }

  /** Espera hasta `count` peticiones a `url` (match() las consume, por eso se acumulan). */
  async function waitForRequests(url: string, count: number): Promise<TestRequest[]> {
    const found: TestRequest[] = [];
    await vi.waitFor(() => {
      found.push(...http.match(url));
      expect(found.length).toBe(count);
    });
    return found;
  }

  async function flushNext(url: string, body: object = fakeChallenge()) {
    const [req] = await waitForRequests(url, 1);
    req.flush(body);
  }

  const forbidden = (code: string, extra: object = {}) => ({
    status: 403,
    statusText: 'Forbidden',
    body: { code, challengeUrl: '/api/auth/challenge', headerName: 'X-Altcha', ...extra },
  });

  afterEach(() => http.verify());

  describe('modo casilla', () => {
    beforeEach(() => setup());

    it('petición protegida → agrega X-Altcha y el cuerpo queda idéntico', async () => {
      const payload = await verifiedCheckbox();
      const body = { username: 'u', password: 'p' };
      const response = firstValueFrom(client.post('/api/auth/login', body));
      const req = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      expect(req.request.headers.get('X-Altcha')).toBe(payload);
      expect(req.request.body).toBe(body);
      req.flush({ status: 'ok' });
      await expect(response).resolves.toEqual({ status: 'ok' });
      expect(service.takePayload()).toBeNull();
    });

    it('petición no protegida → sin cambios', async () => {
      const response = firstValueFrom(client.get('/api/otro'));
      const req = http.expectOne('/api/otro');
      expect(req.request.headers.has('X-Altcha')).toBe(false);
      req.flush({});
      await response;
    });

    it('sin casilla verificada → no envía y emite CaptchaError required', async () => {
      await expect(firstValueFrom(client.post('/api/auth/login', {}))).rejects.toEqual({
        kind: 'required',
      });
      http.expectNone('/api/auth/login');
    });

    it('403 CAPTCHA_EXPIRED → resuelve y reintenta exactamente una vez', async () => {
      await verifiedCheckbox();
      const response = firstValueFrom(client.post('/api/auth/login', {}));
      const first = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      first.flush(forbidden('CAPTCHA_EXPIRED').body, forbidden('CAPTCHA_EXPIRED'));
      await flushNext('/api/auth/challenge');
      const retry = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      expect(retry.request.headers.has('X-Altcha')).toBe(true);
      retry.flush(forbidden('CAPTCHA_EXPIRED').body, forbidden('CAPTCHA_EXPIRED'));
      await expect(response).rejects.toMatchObject({ status: 403 });
      http.expectNone('/api/auth/challenge');
    });

    it('403 CAPTCHA_INVALID → se propaga y reinicia el servicio', async () => {
      await verifiedCheckbox();
      const response = firstValueFrom(client.post('/api/auth/login', {}));
      const req = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      req.flush(forbidden('CAPTCHA_INVALID').body, forbidden('CAPTCHA_INVALID'));
      await expect(response).rejects.toMatchObject({ status: 403 });
      expect(service.state()).toBe('idle');
    });

    it('casilla sin verificar + 403 CAPTCHA_REQUIRED en petición no declarada → required, sin reintento', async () => {
      const response = firstValueFrom(client.post('/api/no-declarada', {}));
      http
        .expectOne('/api/no-declarada')
        .flush(forbidden('CAPTCHA_REQUIRED').body, forbidden('CAPTCHA_REQUIRED'));
      await expect(response).rejects.toEqual({ kind: 'required' });
      http.expectNone('/api/auth/challenge');
    });

    it('respeta método y RegExp de protectedRequests', async () => {
      TestBed.resetTestingModule();
      setup({ protectedRequests: [{ method: 'POST', url: /\/api\/form\/\d+$/ }] });
      const payload = await verifiedCheckbox();
      const response = firstValueFrom(client.post('/api/form/7', {}));
      const req = await vi.waitFor(() => http.expectOne('/api/form/7'));
      expect(req.request.headers.get('X-Altcha')).toBe(payload);
      req.flush({});
      await response;
      const get = firstValueFrom(client.get('/api/form/7'));
      http.expectOne('/api/form/7').flush({});
      await get;
    });

    it('429 → state blocked con Retry-After, sin reintento', async () => {
      await verifiedCheckbox();
      const response = firstValueFrom(client.post('/api/auth/login', {}));
      const req = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      req.flush(
        { code: 'TOO_MANY_ATTEMPTS' },
        { status: 429, statusText: 'Too Many', headers: new HttpHeaders({ 'Retry-After': '300' }) },
      );
      await expect(response).rejects.toMatchObject({ status: 429 });
      expect(service.state()).toBe('blocked');
      expect(service.error()).toEqual({ kind: 'blocked', retryAfterSeconds: 300 });
    });
  });

  describe('modo invisible', () => {
    beforeEach(() => setup({ mode: 'invisible', privacyPolicyUrl: '/privacidad' }));

    it('sin payload previo resuelve antes de enviar y adjunta X-Altcha', async () => {
      const response = firstValueFrom(client.post('/api/auth/login', { a: 1 }));
      await flushNext('/api/auth/challenge');
      const req = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      expect(req.request.headers.has('X-Altcha')).toBe(true);
      req.flush({ ok: true });
      await expect(response).resolves.toEqual({ ok: true });
    });

    it('si la resolución falla, la petición NO se envía y emite CaptchaError', async () => {
      const response = firstValueFrom(client.post('/api/auth/login', {}));
      const [challengeReq] = await waitForRequests('/api/auth/challenge', 1);
      challengeReq.flush({}, { status: 503, statusText: 'Down' });
      await expect(response).rejects.toEqual({ kind: 'unavailable' });
      http.expectNone('/api/auth/login');
    });

    it('dos peticiones simultáneas resuelven dos desafíos distintos', async () => {
      const r1 = firstValueFrom(client.post('/api/auth/login', {}));
      const r2 = firstValueFrom(client.post('/api/auth/login', {}));
      const challenges = await waitForRequests('/api/auth/challenge', 2);
      challenges.forEach((r, i) => r.flush(fakeChallenge(120, 'sig' + i)));
      const reqs = await waitForRequests('/api/auth/login', 2);
      const headers = reqs.map((r) => r.request.headers.get('X-Altcha'));
      expect(new Set(headers).size).toBe(2);
      reqs.forEach((r) => r.flush({}));
      await Promise.all([r1, r2]);
    });

    it('usa la solución anticipada vigente sin volver a resolver', async () => {
      service.prefetch();
      await flushNext('/api/auth/challenge');
      await vi.waitFor(() => expect(service.state()).toBe('verified'));
      const response = firstValueFrom(client.post('/api/auth/login', {}));
      const req = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      http.expectNone('/api/auth/challenge');
      req.flush({});
      await response;
    });

    it('solución anticipada con < 10 s de vigencia → resuelve una nueva', async () => {
      service.prefetch();
      await flushNext('/api/auth/challenge', fakeChallenge(5));
      await vi.waitFor(() => expect(service.state()).toBe('verified'));
      const response = firstValueFrom(client.post('/api/auth/login', {}));
      await flushNext('/api/auth/challenge');
      const req = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      req.flush({});
      await response;
    });

    it('petición no declarada con 403 CAPTCHA_REQUIRED usa challengeUrl y headerName del backend', async () => {
      const response = firstValueFrom(client.post('/api/no-declarada', {}));
      http.expectOne('/api/no-declarada').flush(
        {
          code: 'CAPTCHA_REQUIRED',
          challengeUrl: '/api/seguridad/desafio',
          headerName: 'X-Captcha',
        },
        { status: 403, statusText: 'Forbidden' },
      );
      await flushNext('/api/seguridad/desafio');
      const retry = await vi.waitFor(() => http.expectOne('/api/no-declarada'));
      expect(retry.request.headers.has('X-Captcha')).toBe(true);
      expect(retry.request.headers.has('X-Altcha')).toBe(false);
      retry.flush({ ok: 1 });
      await expect(response).resolves.toEqual({ ok: 1 });
    });

    it('ruta del desafío del frontend inexistente (404) → envía sin cabecera y usa la del 403', async () => {
      const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
      const response = firstValueFrom(client.post('/api/auth/login', {}));
      const [challengeReq] = await waitForRequests('/api/auth/challenge', 1);
      challengeReq.flush({}, { status: 404, statusText: 'Not Found' });
      const first = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      expect(first.request.headers.has('X-Altcha')).toBe(false);
      first.flush(
        {
          code: 'CAPTCHA_REQUIRED',
          challengeUrl: '/api/seguridad/desafio',
          headerName: 'X-Captcha',
        },
        { status: 403, statusText: 'Forbidden' },
      );
      await flushNext('/api/seguridad/desafio');
      const retry = await vi.waitFor(() => http.expectOne('/api/auth/login'));
      expect(retry.request.headers.has('X-Captcha')).toBe(true);
      retry.flush({ ok: 1 });
      await expect(response).resolves.toEqual({ ok: 1 });
      expect(spy).toHaveBeenCalled();
      spy.mockRestore();
    });

    it('S5: 403 de otro origen se propaga sin resolver ni reintentar', async () => {
      const response = firstValueFrom(client.post('https://api.tercero.com/x', {}));
      http
        .expectOne('https://api.tercero.com/x')
        .flush(
          { code: 'CAPTCHA_REQUIRED', challengeUrl: '/c', headerName: 'X-A' },
          { status: 403, statusText: 'Forbidden' },
        );
      await expect(response).rejects.toMatchObject({ status: 403 });
      http.expectNone('/c');
    });

    it('S5: challengeUrl de otro origen o no http(s) y headerName inválido se ignoran', async () => {
      for (const challengeUrl of [
        'https://evil.example/c',
        'javascript:alert(1)',
        '//evil.example/c',
      ]) {
        const response = firstValueFrom(client.post('/api/no-declarada', {}));
        http
          .expectOne('/api/no-declarada')
          .flush(
            { code: 'CAPTCHA_REQUIRED', challengeUrl, headerName: 'X Bad\r\nInjected: 1' },
            { status: 403, statusText: 'Forbidden' },
          );
        await flushNext('/api/auth/challenge');
        const retry = await vi.waitFor(() => http.expectOne('/api/no-declarada'));
        expect(retry.request.headers.has('X-Altcha')).toBe(true);
        retry.flush({});
        await response;
      }
    });

    it('entrada con mode:"checkbox" exige casilla aunque el modo global sea invisible', async () => {
      TestBed.resetTestingModule();
      setup({
        mode: 'invisible',
        privacyPolicyUrl: '/p',
        protectedRequests: [
          { method: 'POST', url: '/api/auth/login', mode: 'checkbox' },
          { method: 'POST', url: '/api/public/consulta' },
        ],
      });
      await expect(firstValueFrom(client.post('/api/auth/login', {}))).rejects.toEqual({
        kind: 'required',
      });
      const consulta = firstValueFrom(client.post('/api/public/consulta', {}));
      await flushNext('/api/auth/challenge');
      const req = await vi.waitFor(() => http.expectOne('/api/public/consulta'));
      expect(req.request.headers.has('X-Altcha')).toBe(true);
      req.flush({});
      await consulta;
    });
  });
});
