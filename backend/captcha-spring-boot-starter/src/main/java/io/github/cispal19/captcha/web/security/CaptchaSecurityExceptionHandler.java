package io.github.cispal19.captcha.web.security;

import io.github.cispal19.captcha.web.CaptchaErrorCode;
import io.github.cispal19.captcha.web.CaptchaProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Convierte {@link CaptchaLockedException} en 429 TOO_MANY_ATTEMPTS con Retry-After. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CaptchaSecurityExceptionHandler {

  private final CaptchaProblems problems;

  public CaptchaSecurityExceptionHandler(CaptchaProblems problems) {
    this.problems = problems;
  }

  @ExceptionHandler(CaptchaLockedException.class)
  public ResponseEntity<ProblemDetail> locked(
      CaptchaLockedException e, HttpServletRequest request) {
    return problems.entity(CaptchaErrorCode.TOO_MANY_ATTEMPTS, request, e.retryAfter());
  }
}
