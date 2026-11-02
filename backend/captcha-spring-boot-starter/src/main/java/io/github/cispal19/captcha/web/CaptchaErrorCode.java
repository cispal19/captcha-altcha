package io.github.cispal19.captcha.web;

import io.github.cispal19.captcha.domain.VerificationOutcome;
import org.springframework.http.HttpStatus;

/** Códigos estables expuestos en {@code ProblemDetail.code}. */
public enum CaptchaErrorCode {
  CAPTCHA_REQUIRED(HttpStatus.FORBIDDEN, "Verificación anti-bot requerida"),
  CAPTCHA_INVALID(HttpStatus.FORBIDDEN, "Verificación anti-bot fallida"),
  CAPTCHA_EXPIRED(HttpStatus.FORBIDDEN, "Verificación anti-bot expirada"),
  TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos"),
  CAPTCHA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de verificación no disponible");

  private final HttpStatus status;
  private final String title;

  CaptchaErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** Los casos de firma, solución, formato y reutilización no se distinguen hacia afuera. */
  public static CaptchaErrorCode from(VerificationOutcome outcome) {
    return switch (outcome) {
      case EXPIRED -> CAPTCHA_EXPIRED;
      case BLOCKED -> TOO_MANY_ATTEMPTS;
      case STORE_UNAVAILABLE -> CAPTCHA_UNAVAILABLE;
      case VERIFIED -> throw new IllegalArgumentException("VERIFIED no es un error");
      default -> CAPTCHA_INVALID;
    };
  }
}
