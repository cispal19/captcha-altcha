package io.github.cispal19.captcha.web.security;

import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.application.service.LoginBlockedException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

/**
 * {@code UserDetailsChecker} para {@code DaoAuthenticationProvider.setPreAuthenticationChecks}:
 * rechaza usuarios o IP bloqueados antes de comprobar la contraseña. Conserva las comprobaciones de
 * estado de cuenta estándar mediante un delegado.
 */
public class BlockedLoginChecker implements UserDetailsChecker {

  private final LoginAttemptLimiter limiter;
  private final UserDetailsChecker delegate;

  public BlockedLoginChecker(LoginAttemptLimiter limiter) {
    this(limiter, new AccountStatusUserDetailsChecker());
  }

  public BlockedLoginChecker(LoginAttemptLimiter limiter, UserDetailsChecker delegate) {
    this.limiter = limiter;
    this.delegate = delegate;
  }

  @Override
  public void check(UserDetails user) {
    try {
      limiter.assertNotBlocked(user.getUsername(), RequestIps.current());
    } catch (LoginBlockedException e) {
      throw new CaptchaLockedException(e.retryAfter());
    }
    delegate.check(user);
  }
}
