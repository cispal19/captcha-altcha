package io.github.cispal19.captcha.web.security;

import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

/**
 * Registra los fallos de credenciales por usuario e IP y reinicia el contador del usuario tras un
 * inicio de sesión exitoso. Se desactiva con {@code login-attempts.auto-register}.
 */
public class AuthenticationEventsListener {

  private final LoginAttemptLimiter limiter;

  public AuthenticationEventsListener(LoginAttemptLimiter limiter) {
    this.limiter = limiter;
  }

  @EventListener
  public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
    limiter.recordFailure(
        event.getAuthentication().getName(),
        RequestIps.from(event.getAuthentication().getDetails()));
  }

  @EventListener
  public void onSuccess(AuthenticationSuccessEvent event) {
    limiter.reset(event.getAuthentication().getName());
  }
}
