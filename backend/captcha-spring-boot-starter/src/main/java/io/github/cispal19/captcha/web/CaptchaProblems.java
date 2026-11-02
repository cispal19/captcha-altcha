package io.github.cispal19.captcha.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Construye las respuestas RFC 9457 de la librería. Los 403 incluyen {@code challengeUrl} y {@code
 * headerName} para que el frontend resuelva y reintente.
 */
public class CaptchaProblems {

  /** Textos de {@code detail} por código. */
  public record Messages(
      String required, String invalid, String expired, String tooManyAttempts, String unavailable) {

    String of(CaptchaErrorCode code) {
      return switch (code) {
        case CAPTCHA_REQUIRED -> required;
        case CAPTCHA_INVALID -> invalid;
        case CAPTCHA_EXPIRED -> expired;
        case TOO_MANY_ATTEMPTS -> tooManyAttempts;
        case CAPTCHA_UNAVAILABLE -> unavailable;
      };
    }
  }

  private final Messages messages;
  private final String challengePath;
  private final String headerName;

  public CaptchaProblems(Messages messages, String challengePath, String headerName) {
    this.messages = messages;
    this.challengePath = challengePath;
    this.headerName = headerName;
  }

  public ProblemDetail problem(CaptchaErrorCode code, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), messages.of(code));
    problem.setType(
        URI.create("urn:captcha:error:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setTitle(code.title());
    problem.setProperty("code", code.name());
    if (code.status().value() == HttpServletResponse.SC_FORBIDDEN) {
      String contextPath = request == null ? "" : request.getContextPath();
      problem.setProperty("challengeUrl", contextPath + challengePath);
      problem.setProperty("headerName", headerName);
    }
    return problem;
  }

  public ResponseEntity<ProblemDetail> entity(
      CaptchaErrorCode code, HttpServletRequest request, Duration retryAfter) {
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (retryAfter != null) {
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(retryAfter)));
    }
    return builder.body(problem(code, request));
  }

  /** Escribe la respuesta desde un filtro de servlet (fuera de Spring MVC). */
  public void write(
      CaptchaErrorCode code,
      HttpServletRequest request,
      HttpServletResponse response,
      Duration retryAfter)
      throws IOException {
    ProblemDetail problem = problem(code, request);
    JSONObject body =
        new JSONObject()
            .put("type", String.valueOf(problem.getType()))
            .put("title", problem.getTitle())
            .put("status", problem.getStatus())
            .put("detail", problem.getDetail());
    if (problem.getProperties() != null) {
      problem.getProperties().forEach(body::put);
    }
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    if (retryAfter != null) {
      response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(retryAfter)));
    }
    response.getWriter().write(body.toString());
  }

  static long retryAfterSeconds(Duration retryAfter) {
    long seconds = retryAfter.toSeconds();
    return retryAfter.toNanosPart() > 0 || seconds == 0 ? seconds + 1 : seconds;
  }
}
