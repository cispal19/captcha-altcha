import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideCaptcha } from '../config/provide-captcha';
import { POW_SOLVER } from '../solver/pow-solver';
import { FakeSolver, fakeChallenge } from '../testing/fakes';
import { CaptchaService } from './captcha.service';

describe('CaptchaService', () => {
  let service: CaptchaService;
  let http: HttpTestingController;
  let solver: FakeSolver;

  beforeEach(() => {
    solver = new FakeSolver();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideCaptcha({}),
        { provide: POW_SOLVER, useValue: solver },
      ],
    });
    service = TestBed.inject(CaptchaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  async function flushChallenge(validSeconds = 120) {
    await Promise.resolve();
    http.expectOne('/api/auth/challenge').flush(fakeChallenge(validSeconds));
  }

  it('state: idle → verifying → verified; solve devuelve base64 de {challenge, solution}', async () => {
    expect(service.state()).toBe('idle');
    const promise = service.solve();
    expect(service.state()).toBe('verifying');
    await flushChallenge();
    const payload = await promise;
    expect(service.state()).toBe('verified');
    const decoded = JSON.parse(atob(payload));
    expect(Object.keys(decoded)).toEqual(['challenge', 'solution']);
    expect(decoded.challenge.signature).toBe('ab');
    expect(decoded.solution.counter).toBe(1);
  });

  it('takePayload devuelve el payload una sola vez', async () => {
    const promise = service.solve();
    await flushChallenge();
    const payload = await promise;
    expect(service.takePayload()).toBe(payload);
    expect(service.takePayload()).toBeNull();
    expect(service.state()).toBe('idle');
  });

  it('takePayload(minValidity) descarta soluciones próximas a expirar', async () => {
    const promise = service.solve();
    await flushChallenge(5);
    await promise;
    expect(service.takePayload(10_000)).toBeNull();
  });

  it('el payload expira localmente al alcanzar expiresAt → expired', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const promise = service.solve();
    await flushChallenge(2);
    await promise;
    vi.advanceTimersByTime(2500);
    expect(service.state()).toBe('expired');
    expect(service.takePayload()).toBeNull();
  });

  it('reset vuelve a idle; un error deja el estado en error', async () => {
    const promise = service.solve();
    await Promise.resolve();
    http.expectOne('/api/auth/challenge').flush({}, { status: 503, statusText: 'Down' });
    await expect(promise).rejects.toEqual({ kind: 'unavailable' });
    expect(service.state()).toBe('error');
    expect(service.error()).toEqual({ kind: 'unavailable' });
    service.reset();
    expect(service.state()).toBe('idle');
  });

  it('E1: unsupported deja el estado en error con kind unsupported', async () => {
    TestBed.inject(POW_SOLVER).solve = () => Promise.reject({ kind: 'unsupported' });
    const promise = service.solve();
    await flushChallenge();
    await expect(promise).rejects.toEqual({ kind: 'unsupported' });
    expect(service.error()?.kind).toBe('unsupported');
  });

  it('429 al pedir el desafío → blocked con retryAfterSeconds', async () => {
    const promise = service.solve();
    await Promise.resolve();
    http
      .expectOne('/api/auth/challenge')
      .flush(
        {},
        { status: 429, statusText: 'Too Many', headers: new HttpHeaders({ 'Retry-After': '300' }) },
      );
    await expect(promise).rejects.toEqual({ kind: 'blocked', retryAfterSeconds: 300 });
    expect(service.state()).toBe('blocked');
  });

  it('prefetch resuelve una vez y no duplica', async () => {
    service.prefetch();
    service.prefetch();
    await flushChallenge();
    await vi.waitFor(() => expect(service.state()).toBe('verified'));
    service.prefetch();
    http.expectNone('/api/auth/challenge');
    expect(solver.calls).toBe(1);
  });

  it('solveForRequest no guarda la solución', async () => {
    const promise = service.solveForRequest('/api/otra/ruta');
    await Promise.resolve();
    http.expectOne('/api/otra/ruta').flush(fakeChallenge());
    expect(await promise).toBeTruthy();
    expect(service.takePayload()).toBeNull();
  });
});
