import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CAPTCHA_CONFIG,
  CaptchaCheckboxComponent,
  CaptchaNoticeComponent,
} from '@cispal19/captcha-angular';
import { feedbackFor } from '../feedback';

/** Login existente: solo agrega el componente; el interceptor adjunta la solución. */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, CaptchaCheckboxComponent, CaptchaNoticeComponent],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly http = inject(HttpClient);
  protected readonly checkbox = inject(CAPTCHA_CONFIG).mode === 'checkbox';
  protected readonly message = signal('');
  protected readonly sending = signal(false);

  protected readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    ...(this.checkbox ? { captcha: new FormControl(false) } : {}),
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.message.set(
        this.checkbox ? 'Complete los campos y marque la casilla.' : 'Complete los campos.',
      );
      return;
    }
    this.sending.set(true);
    this.message.set('');
    const { username, password } = this.form.getRawValue();
    this.http.post<{ message: string }>('/api/auth/login', { username, password }).subscribe({
      next: (res) => {
        this.message.set(res.message);
        this.sending.set(false);
      },
      error: (error) => {
        this.message.set(feedbackFor(error));
        this.sending.set(false);
      },
    });
  }
}
