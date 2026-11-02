package io.github.cispal19.captcha.application.service;

import java.time.Duration;

/** El origen (IP o usuario) está bloqueado temporalmente. */
public class LoginBlockedException extends RuntimeException {

  private final Duration retryAfter;

  public LoginBlockedException(Duration retryAfter) {
    super("Origen bloqueado temporalmente");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
