package io.github.cispal19.captcha.web;

import io.github.cispal19.captcha.application.service.AntiBotService;
import io.github.cispal19.captcha.application.service.VerificationResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exige y verifica la solución en las rutas protegidas antes de la autenticación. */
public class AntiBotVerificationFilter extends OncePerRequestFilter {

  private final AntiBotService service;
  private final ProtectedRequestMatcher matcher;
  private final CaptchaProblems problems;
  private final String headerName;

  public AntiBotVerificationFilter(
      AntiBotService service,
      ProtectedRequestMatcher matcher,
      CaptchaProblems problems,
      String headerName) {
    this.service = service;
    this.matcher = matcher;
    this.problems = problems;
    this.headerName = headerName;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !matcher.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(headerName);
    if (header == null || header.isBlank()) {
      problems.write(CaptchaErrorCode.CAPTCHA_REQUIRED, request, response, null);
      return;
    }
    VerificationResult result = service.verify(header, ClientOrigins.of(request));
    if (result.isVerified()) {
      chain.doFilter(request, response);
      return;
    }
    problems.write(
        CaptchaErrorCode.from(result.outcome()),
        request,
        response,
        result.retryAfter().orElse(null));
  }
}
