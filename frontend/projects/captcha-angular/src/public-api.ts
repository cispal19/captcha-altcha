/*
 * API pública de @cispal19/captcha-angular (specs/001-captcha-library/contracts/angular-public-api.md).
 */
export { provideCaptcha } from './lib/config/provide-captcha';
export { CAPTCHA_CONFIG } from './lib/config/captcha-config';
export type {
  CaptchaConfig,
  CaptchaError,
  CaptchaMode,
  CaptchaState,
  CaptchaTexts,
  ProtectedRequest,
} from './lib/config/captcha-config';
export { CaptchaService } from './lib/service/captcha.service';
export { captchaInterceptor } from './lib/interceptor/captcha.interceptor';
export { CaptchaCheckboxComponent } from './lib/components/captcha-checkbox.component';
export { CaptchaNoticeComponent } from './lib/components/captcha-notice.component';
export { POW_SOLVER } from './lib/solver/pow-solver';
export type { PowSolver } from './lib/solver/pow-solver';
