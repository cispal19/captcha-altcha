import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { CAPTCHA_CONFIG, CaptchaError, CaptchaState } from '../config/captcha-config';
import { interpolate } from '../i18n/resolve-texts';
import { CaptchaService } from '../service/captcha.service';

let nextId = 0;

/**
 * Casilla "No soy un robot" (modo casilla). Casilla nativa, región `aria-live` y estilos
 * personalizables con variables CSS `--captcha-*`.
 */
@Component({
  selector: 'captcha-checkbox',
  templateUrl: './captcha-checkbox.component.html',
  styleUrl: './captcha-checkbox.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.Emulated,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CaptchaCheckboxComponent),
      multi: true,
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => CaptchaCheckboxComponent),
      multi: true,
    },
  ],
  host: { class: 'captcha-host' },
})
export class CaptchaCheckboxComponent implements ControlValueAccessor, Validator {
  private readonly service = inject(CaptchaService);
  protected readonly texts = inject(CAPTCHA_CONFIG).texts;

  readonly disabled = input(false);
  readonly verified = output<string>();
  readonly stateChange = output<CaptchaState>();
  readonly failed = output<CaptchaError>();

  protected readonly inputId = `captcha-checkbox-${nextId++}`;
  protected readonly statusId = `${this.inputId}-status`;
  private readonly formDisabled = signal(false);
  protected readonly state = this.service.state;
  protected readonly isDisabled = computed(
    () =>
      this.disabled() ||
      this.formDisabled() ||
      this.state() === 'verifying' ||
      this.state() === 'blocked',
  );
  protected readonly statusText = computed(() => {
    const error = this.service.error();
    switch (this.state()) {
      case 'verifying':
        return this.texts.verifying;
      case 'verified':
        return this.texts.verified;
      case 'expired':
        return this.texts.expired;
      case 'blocked':
        return interpolate(this.texts.blocked, {
          minutes: Math.max(1, Math.ceil((error?.retryAfterSeconds ?? 60) / 60)),
        });
      case 'error':
        return error?.kind === 'unsupported' ? this.texts.unsupported : this.texts.error;
      default:
        return '';
    }
  });

  private lastEmitted: CaptchaState | null = null;
  private onChange: (value: boolean) => void = () => undefined;
  private onTouched: () => void = () => undefined;
  private onValidatorChange: () => void = () => undefined;

  constructor() {
    effect(() => {
      const state = this.state();
      untracked(() => this.publish(state));
    });
  }

  protected async onToggle(event: Event): Promise<void> {
    const checked = (event.target as HTMLInputElement).checked;
    this.onTouched();
    if (!checked) {
      this.service.reset();
      return;
    }
    await this.verify();
  }

  protected retry(): Promise<void> {
    return this.verify();
  }

  private async verify(): Promise<void> {
    const solving = this.service.solve();
    // Las transiciones iniciadas aquí se publican sin esperar al effect.
    this.publish(this.state());
    try {
      const payload = await solving;
      this.publish(this.state());
      this.verified.emit(payload);
    } catch (error) {
      this.publish(this.state());
      this.failed.emit(error as CaptchaError);
    }
  }

  private publish(state: CaptchaState): void {
    if (state === this.lastEmitted) {
      return;
    }
    this.lastEmitted = state;
    this.stateChange.emit(state);
    this.onChange(state === 'verified');
    this.onValidatorChange();
  }

  writeValue(value: unknown): void {
    if (!value && this.state() !== 'idle') {
      this.service.reset();
    }
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.formDisabled.set(isDisabled);
  }

  validate(): ValidationErrors | null {
    return this.state() === 'verified' ? null : { captchaRequired: true };
  }

  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }
}
