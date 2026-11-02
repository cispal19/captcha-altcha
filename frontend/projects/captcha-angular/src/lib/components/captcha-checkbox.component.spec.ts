import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, ViewEncapsulation, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CaptchaConfig, CaptchaState } from '../config/captcha-config';
import { provideCaptcha } from '../config/provide-captcha';
import { CaptchaService } from '../service/captcha.service';
import { POW_SOLVER } from '../solver/pow-solver';
import { FakeSolver, fakeChallenge } from '../testing/fakes';
import { CaptchaCheckboxComponent } from './captcha-checkbox.component';

@Component({
  imports: [CaptchaCheckboxComponent, ReactiveFormsModule],
  template: `<captcha-checkbox
    [formControl]="control"
    (verified)="payloads.push($event)"
    (stateChange)="states.push($event)"
    (failed)="failures.push($event.kind)"
  />`,
})
class HostComponent {
  control = new FormControl(false);
  payloads: string[] = [];
  states: CaptchaState[] = [];
  failures: string[] = [];
}

describe('CaptchaCheckboxComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let http: HttpTestingController;
  let host: HostComponent;

  function setup(config: Partial<CaptchaConfig> = {}) {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideCaptcha(config),
        { provide: POW_SOLVER, useValue: new FakeSolver() },
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const checkbox = () => el().querySelector('input[type="checkbox"]') as HTMLInputElement;
  const status = () => el().querySelector('[role="status"]') as HTMLElement;

  afterEach(() => http.verify());

  it('casilla nativa con la etiqueta "No soy un robot" asociada', () => {
    setup();
    const label = el().querySelector('label') as HTMLLabelElement;
    expect(checkbox()).toBeTruthy();
    expect(label.textContent?.trim()).toBe('No soy un robot');
    expect(label.htmlFor).toBe(checkbox().id);
  });

  it('al marcar resuelve, emite verified y el FormControl pasa a válido', async () => {
    setup();
    expect(host.control.valid).toBe(false);
    checkbox().click();
    await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge()));
    await vi.waitFor(() => expect(host.payloads.length).toBe(1));
    await fixture.whenStable();
    expect(status().textContent?.trim()).toBe('Verificado');
    expect(checkbox().checked).toBe(true);
    expect(host.control.value).toBe(true);
    expect(host.control.valid).toBe(true);
    expect(host.states).toContain('verifying');
    expect(host.states).toContain('verified');
  });

  it('en error muestra el texto y un botón "Reintentar" que vuelve a resolver', async () => {
    setup();
    checkbox().click();
    await vi.waitFor(() =>
      http.expectOne('/api/auth/challenge').flush({}, { status: 503, statusText: 'Down' }),
    );
    await vi.waitFor(() => expect(host.failures).toEqual(['unavailable']));
    await fixture.whenStable();
    expect(status().textContent?.trim()).toBe('No se pudo verificar. Reintentar');
    const retry = el().querySelector('button') as HTMLButtonElement;
    expect(retry.textContent?.trim()).toBe('Reintentar');
    retry.click();
    await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge()));
    await vi.waitFor(() => expect(host.payloads.length).toBe(1));
  });

  it('expirada → desmarca y muestra el texto de expiración', async () => {
    setup();
    checkbox().click();
    await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge(1)));
    await vi.waitFor(() => expect(host.payloads.length).toBe(1));
    await vi.waitFor(
      async () => {
        await fixture.whenStable();
        expect(status().textContent?.trim()).toBe(
          'La verificación expiró. Vuelva a marcar la casilla',
        );
      },
      { timeout: 3000 },
    );
    expect(checkbox().checked).toBe(false);
    expect(host.control.valid).toBe(false);
  });

  it('desmarcar reinicia; reset del formulario también', async () => {
    setup();
    checkbox().click();
    await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge()));
    await vi.waitFor(() => expect(host.payloads.length).toBe(1));
    checkbox().click();
    await fixture.whenStable();
    expect(TestBed.inject(CaptchaService).state()).toBe('idle');
    host.control.reset();
    expect(host.control.value).toBeNull();
  });

  it('bloqueado muestra los minutos y deshabilita la casilla', async () => {
    setup();
    checkbox().click();
    await vi.waitFor(() =>
      http.expectOne('/api/auth/challenge').flush(
        {},
        {
          status: 429,
          statusText: 'Too Many',
          headers: new HttpHeaders({ 'Retry-After': '300' }),
        },
      ),
    );
    await vi.waitFor(() => expect(host.failures).toEqual(['blocked']));
    await fixture.whenStable();
    expect(status().textContent?.trim()).toBe('Demasiados intentos. Intente en 5 min');
    expect(checkbox().disabled).toBe(true);
  });

  it('E1: navegador sin WebCrypto muestra el texto unsupported', async () => {
    setup();
    TestBed.inject(POW_SOLVER).solve = () => Promise.reject({ kind: 'unsupported' });
    checkbox().click();
    await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge()));
    await vi.waitFor(() => expect(host.failures).toEqual(['unsupported']));
    await fixture.whenStable();
    expect(status().textContent).toContain('Su navegador no permite completar la verificación');
  });

  it('textos e idioma configurables', () => {
    setup({ language: 'en', texts: { checkboxLabel: 'Soy humano' } });
    expect(el().querySelector('label')?.textContent?.trim()).toBe('Soy humano');
  });

  it('variables CSS --captcha-* definidas por el proyecto y encapsulación Emulated', () => {
    setup();
    const hostEl = el().querySelector('captcha-checkbox') as HTMLElement;
    hostEl.style.setProperty('--captcha-radius', '12px');
    expect(getComputedStyle(hostEl).getPropertyValue('--captcha-radius').trim()).toBe('12px');
    const def = (CaptchaCheckboxComponent as unknown as { ɵcmp: { encapsulation: number } }).ɵcmp;
    expect(def.encapsulation).toBe(ViewEncapsulation.Emulated);
  });

  describe('accesibilidad', () => {
    it('región aria-live polite con role=status y aria-busy mientras verifica', async () => {
      setup();
      expect(status().getAttribute('aria-live')).toBe('polite');
      expect(checkbox().getAttribute('aria-describedby')).toBe(status().id);
      checkbox().click();
      await fixture.whenStable();
      expect(status().getAttribute('aria-busy')).toBe('true');
      expect(status().textContent?.trim()).toBe('Verificando…');
      await vi.waitFor(() => http.expectOne('/api/auth/challenge').flush(fakeChallenge()));
      await vi.waitFor(() => expect(host.payloads.length).toBe(1));
      await fixture.whenStable();
      expect(status().getAttribute('aria-busy')).toBe('false');
    });

    it('casilla y "Reintentar" alcanzables con teclado; el foco no se mueve solo', async () => {
      setup();
      checkbox().focus();
      expect(document.activeElement).toBe(checkbox());
      expect(checkbox().tabIndex).toBe(0);
      checkbox().click();
      await vi.waitFor(() =>
        http.expectOne('/api/auth/challenge').flush({}, { status: 503, statusText: 'Down' }),
      );
      await vi.waitFor(() => expect(host.failures.length).toBe(1));
      await fixture.whenStable();
      const retry = el().querySelector('button') as HTMLButtonElement;
      expect(retry.tabIndex).toBe(0);
      expect(retry.type).toBe('button');
    });
  });
});
