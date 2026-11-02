import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CaptchaConfig } from '../config/captcha-config';
import { provideCaptcha } from '../config/provide-captcha';
import { POW_SOLVER } from '../solver/pow-solver';
import { FakeSolver, fakeChallenge } from '../testing/fakes';
import { CaptchaNoticeComponent } from './captcha-notice.component';

describe('CaptchaNoticeComponent', () => {
  let http: HttpTestingController;

  function render(config: Partial<CaptchaConfig>) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideCaptcha({ mode: 'invisible', privacyPolicyUrl: '/privacidad', ...config }),
        { provide: POW_SOLVER, useValue: new FakeSolver() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CaptchaNoticeComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  it('muestra el aviso y el enlace de privacidad con indicación de ventana nueva', () => {
    const root = render({ solveOn: 'submit' });
    expect(root.textContent).toContain('Este formulario está protegido contra bots.');
    const link = root.querySelector('a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/privacidad');
    expect(link.rel).toBe('noopener');
    expect(link.target).toBe('_blank');
    expect(link.textContent).toContain('Política de privacidad');
    expect(link.textContent).toContain('(se abre en una ventana nueva)');
  });

  it('sin privacyPolicyUrl muestra solo el aviso, sin enlace', () => {
    const root = render({ solveOn: 'submit', privacyPolicyUrl: undefined });
    expect(root.textContent).toContain('Este formulario está protegido contra bots.');
    expect(root.querySelector('a')).toBeNull();
  });

  it('el texto del aviso es configurable', () => {
    const root = render({ solveOn: 'submit', texts: { invisibleNotice: 'Acceso protegido.' } });
    expect(root.textContent).toContain('Acceso protegido.');
    expect(root.textContent).not.toContain('protegido contra bots');
  });

  it("con solveOn='load' inicia la resolución anticipada al montarse", async () => {
    render({ solveOn: 'load' });
    await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge()));
  });

  it("con solveOn='submit' no pide desafío al montarse", () => {
    render({ solveOn: 'submit' });
    http.expectNone('/api/auth/challenge');
  });
});
