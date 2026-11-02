package io.github.cispal19.captcha.web;

import io.github.cispal19.captcha.application.port.StoreUnavailableException;
import io.github.cispal19.captcha.application.service.LoginBlockedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Responde a las excepciones de la librería con el mismo ProblemDetail que el filtro. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CaptchaExceptionHandler {

  private final CaptchaProblems problems;

  public CaptchaExceptionHandler(CaptchaProblems problems) {
    this.problems = problems;
  }

  @ExceptionHandler(CaptchaVerificationException.class)
  public ResponseEntity<ProblemDetail> verificationFailed(
      CaptchaVerificationException e, HttpServletRequest request) {
    CaptchaErrorCode code =
        e.outcome().map(CaptchaErrorCode::from).orElse(CaptchaErrorCode.CAPTCHA_REQUIRED);
    return problems.entity(code, request, e.retryAfter().orElse(null));
  }

  @ExceptionHandler(LoginBlockedException.class)
  public ResponseEntity<ProblemDetail> blocked(
      LoginBlockedException e, HttpServletRequest request) {
    return problems.entity(CaptchaErrorCode.TOO_MANY_ATTEMPTS, request, e.retryAfter());
  }

  @ExceptionHandler(StoreUnavailableException.class)
  public ResponseEntity<ProblemDetail> unavailable(
      StoreUnavailableException e, HttpServletRequest request) {
    return problems.entity(CaptchaErrorCode.CAPTCHA_UNAVAILABLE, request, null);
  }
}
