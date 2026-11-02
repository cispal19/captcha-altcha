const ABSOLUTE = /^[a-z][a-z0-9+.-]*:/i;
const HEADER_TOKEN = /^[A-Za-z0-9-]{1,64}$/;

function currentOrigin(): string {
  return globalThis.location?.origin ?? 'http://localhost';
}

/** `true` si la URL es relativa o del mismo origen que la aplicación. */
export function isSameOrigin(url: string): boolean {
  if (!ABSOLUTE.test(url) && !url.startsWith('//')) {
    return true;
  }
  try {
    return new URL(url, currentOrigin()).origin === currentOrigin();
  } catch {
    return false;
  }
}

/** Acepta `challengeUrl` del backend solo si es relativa o http(s) del mismo origen. */
export function safeChallengeUrl(value: unknown): string | null {
  if (typeof value !== 'string' || value.length === 0) {
    return null;
  }
  if (value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\')) {
    return value;
  }
  try {
    const parsed = new URL(value, currentOrigin());
    const http = parsed.protocol === 'http:' || parsed.protocol === 'https:';
    return http && parsed.origin === currentOrigin() ? value : null;
  } catch {
    return null;
  }
}

/** Acepta `headerName` del backend solo si es un token HTTP válido. */
export function safeHeaderName(value: unknown): string | null {
  return typeof value === 'string' && HEADER_TOKEN.test(value) ? value : null;
}

/** Ruta sin origen ni query, para comparar con reglas de tipo string. */
export function pathOf(url: string): string {
  try {
    return new URL(url, currentOrigin()).pathname;
  } catch {
    return url;
  }
}
