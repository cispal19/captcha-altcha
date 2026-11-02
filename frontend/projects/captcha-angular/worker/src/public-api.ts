/*
 * Entrada secundaria @cispal19/captcha-angular/worker: resolución PoW sin dependencias de Angular.
 */
export {
  deriveKeyFor,
  handleSolveMessage,
  installCaptchaWorker,
  solvePow,
  webCryptoAvailable,
} from './solve';
export type { SolveRequest, SolveResponse } from './solve';
