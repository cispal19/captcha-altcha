import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CAPTCHA_CONFIG } from '../config/captcha-config';
import { CaptchaService } from '../service/captcha.service';

/**
 * Aviso visible y accesible del modo invisible. Con `solveOn='load'` inicia la
 * resolución anticipada al montarse.
 */
@Component({
  selector: 'captcha-notice',
  templateUrl: './captcha-notice.component.html',
  styleUrl: './captcha-notice.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaptchaNoticeComponent implements OnInit {
  private readonly config = inject(CAPTCHA_CONFIG);
  private readonly service = inject(CaptchaService);
  protected readonly texts = this.config.texts;
  protected readonly privacyPolicyUrl = this.config.privacyPolicyUrl;

  ngOnInit(): void {
    if (this.config.solveOn === 'load') {
      this.service.prefetch();
    }
  }
}
