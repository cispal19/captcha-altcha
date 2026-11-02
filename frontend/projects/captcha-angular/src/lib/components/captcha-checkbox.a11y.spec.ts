import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import axe from 'axe-core';
import { provideCaptcha } from '../config/provide-captcha';
import { POW_SOLVER } from '../solver/pow-solver';
import { FakeSolver, fakeChallenge } from '../testing/fakes';
import { CaptchaCheckboxComponent } from './captcha-checkbox.component';
import { CaptchaNoticeComponent } from './captcha-notice.component';

async function violations(element: Element) {
  const result = await axe.run(element, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } },
  });
  return result.violations.map((v) => `${v.id}: ${v.help}`);
}

describe('axe-core WCAG 2.1 A/AA', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideCaptcha({ privacyPolicyUrl: '/privacidad', solveOn: 'submit' }),
        { provide: POW_SOLVER, useValue: new FakeSolver() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('casilla en idle, verifying, verified, error, expired y blocked: 0 violaciones', async () => {
    const fixture = TestBed.createComponent(CaptchaCheckboxComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    document.body.appendChild(root);
    const input = root.querySelector('input') as HTMLInputElement;
    expect(await violations(root)).toEqual([]);

    input.click();
    await fixture.whenStable();
    expect(await violations(root)).toEqual([]);

    await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge(1)));
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(input.checked).toBe(true);
    });
    expect(await violations(root)).toEqual([]);

    await vi.waitFor(
      async () => {
        await fixture.whenStable();
        expect(root.textContent).toContain('expiró');
      },
      { timeout: 3000 },
    );
    expect(await violations(root)).toEqual([]);

    input.click();
    await vi.waitFor(() =>
      http.expectOne('/api/auth/challenge').flush({}, { status: 503, statusText: 'Down' }),
    );
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(root.querySelector('button')).toBeTruthy();
    });
    expect(await violations(root)).toEqual([]);

    (root.querySelector('button') as HTMLButtonElement).click();
    await vi.waitFor(() =>
      http.expectOne('/api/auth/challenge').flush(
        {},
        {
          status: 429,
          statusText: 'Too Many',
          headers: new HttpHeaders({ 'Retry-After': '60' }),
        },
      ),
    );
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(root.textContent).toContain('Demasiados intentos');
    });
    expect(await violations(root)).toEqual([]);
    root.remove();
  });

  it('aviso del modo invisible: 0 violaciones y texto visible', async () => {
    const fixture = TestBed.createComponent(CaptchaNoticeComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    document.body.appendChild(root);
    expect(root.textContent).toContain('Este formulario está protegido contra bots.');
    expect(await violations(root)).toEqual([]);
    root.remove();
  });
});
