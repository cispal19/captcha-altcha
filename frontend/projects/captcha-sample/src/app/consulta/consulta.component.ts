import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CaptchaNoticeComponent } from '@cispal19/captcha-angular';
import { feedbackFor } from '../feedback';

/** Formulario público en modo invisible. */
@Component({
  selector: 'app-consulta',
  imports: [ReactiveFormsModule, CaptchaNoticeComponent],
  templateUrl: './consulta.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsultaComponent {
  private readonly http = inject(HttpClient);
  protected readonly form = new FormGroup({
    dni: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly message = signal('');

  protected submit(): void {
    this.http.post<{ message: string }>('/api/public/consulta', this.form.getRawValue()).subscribe({
      next: (res) => this.message.set(res.message),
      error: (error) => this.message.set(feedbackFor(error)),
    });
  }
}
