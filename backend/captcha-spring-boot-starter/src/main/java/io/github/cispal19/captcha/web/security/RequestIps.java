package io.github.cispal19.captcha.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** IP de la petición en curso para la integración con Spring Security. */
final class RequestIps {

  private RequestIps() {}

  static String from(Object authenticationDetails) {
    if (authenticationDetails instanceof WebAuthenticationDetails details
        && details.getRemoteAddress() != null) {
      return details.getRemoteAddress();
    }
    return current();
  }

  static String current() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      HttpServletRequest request = servlet.getRequest();
      return request.getRemoteAddr();
    }
    return null;
  }
}
