package io.github.cispal19.captcha.web.security;

import java.time.Duration;
import org.springframework.security.authentication.LockedException;

/** Cuenta u origen bloqueado temporalmente por la librería (429 TOO_MANY_ATTEMPTS). */
public class CaptchaLockedException extends LockedException {

  private final transient Duration retryAfter;

  public CaptchaLockedException(Duration retryAfter) {
    super("Demasiados intentos");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
