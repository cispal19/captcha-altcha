package io.github.cispal19.captcha.web;

import io.github.cispal19.captcha.domain.Origin;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Obtiene el origen de la petición con {@code getRemoteAddr()}; los proxies se resuelven con {@code
 * server.forward-headers-strategy} de Spring Boot (data-model §1, Origin).
 */
public final class ClientOrigins {

  private ClientOrigins() {}

  public static Origin of(HttpServletRequest request) {
    return Origin.of(request.getRemoteAddr());
  }
}
