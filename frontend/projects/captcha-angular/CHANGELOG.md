# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y en versionado
semántico.

## [1.1.0] - 2026-10-21

### Changed
- `privacyPolicyUrl` es opcional también en modo invisible. Sin él, `<captcha-notice>` muestra solo
  el aviso (configurable con `texts.invisibleNotice`), sin enlace.

## [1.0.0] - 2026-10-21

### Added
- `provideCaptcha`, `captchaInterceptor` y `CaptchaService` (signals).
- `<captcha-checkbox>`: casilla accesible WCAG 2.1 AA con `ControlValueAccessor`.
- `<captcha-notice>`: aviso del modo invisible, con resolución anticipada.
- Modo por formulario, reintento único guiado por el backend (`challengeUrl` y `headerName`, solo
  del mismo origen) y diagnóstico de ruta ante un 404.
- Entrada secundaria `@cispal19/captcha-angular/worker` para resolver en un Web Worker.
- Textos en español e inglés reemplazables y variables CSS `--captcha-*`.
