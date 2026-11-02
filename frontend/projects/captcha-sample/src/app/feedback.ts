import { HttpErrorResponse } from '@angular/common/http';

/** Traduce el resultado del envío a un mensaje para el usuario (solo demo). */
export function feedbackFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401) {
      return 'Usuario o contraseña incorrectos';
    }
    if (error.status === 429) {
      return 'Demasiados intentos. Intente más tarde.';
    }
    return error.error?.detail ?? 'No se pudo completar la operación';
  }
  const kind = (error as { kind?: string })?.kind;
  if (kind === 'required') {
    return 'Marque la casilla "No soy un robot".';
  }
  return 'No se pudo verificar. Reintentar';
}
