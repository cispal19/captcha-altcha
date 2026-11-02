import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { CaptchaMode, captchaInterceptor, provideCaptcha } from '@cispal19/captcha-angular';
import { environment } from '../environments/environment';
import { routes } from './app.routes';

const params = new URLSearchParams(globalThis.location?.search ?? '');
const mode = (params.get('mode') as CaptchaMode | null) ?? environment.captchaMode;

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([captchaInterceptor])),
    provideCaptcha({
      mode,
      privacyPolicyUrl: environment.privacyPolicyUrl,
      protectedRequests: [
        { method: 'POST', url: '/api/auth/login' },
        { method: 'POST', url: '/api/public/consulta', mode: 'invisible' },
      ],
      workerFactory: () =>
        new Worker(new URL('./captcha.worker', import.meta.url), { type: 'module' }),
    }),
  ],
};
