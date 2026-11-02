/// <reference lib="webworker" />
// Worker del proyecto consumidor: una línea delega en la librería.
import { installCaptchaWorker } from '@cispal19/captcha-angular/worker';

installCaptchaWorker();
