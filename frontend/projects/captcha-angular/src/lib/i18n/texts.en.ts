import { CaptchaTexts } from '../config/captcha-config';

export const TEXTS_EN: CaptchaTexts = {
  checkboxLabel: "I'm not a robot",
  verifying: 'Verifying…',
  verified: 'Verified',
  error: 'Verification failed. Retry',
  retry: 'Retry',
  expired: 'Verification expired. Please check the box again',
  blocked: 'Too many attempts. Try again in {minutes} min',
  unsupported:
    'Your browser cannot complete the verification. Use a secure connection (HTTPS) or an up-to-date browser',
  invisibleNotice: 'This form is protected against bots.',
  privacyLink: 'Privacy policy',
  newWindow: '(opens in a new window)',
};
