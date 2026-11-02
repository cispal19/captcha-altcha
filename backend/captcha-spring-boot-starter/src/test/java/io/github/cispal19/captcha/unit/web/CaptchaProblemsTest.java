package io.github.cispal19.captcha.unit.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.domain.VerificationOutcome;
import io.github.cispal19.captcha.web.CaptchaErrorCode;
import io.github.cispal19.captcha.web.CaptchaProblems;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("CaptchaProblems: RFC 9457 sin detalles internos")
class CaptchaProblemsTest {

  private final CaptchaProblems problems =
      new CaptchaProblems(
          new CaptchaProblems.Messages("req", "inv", "exp", "many", "down"),
          "/api/auth/challenge",
          "X-Altcha");

  @Test
  @DisplayName("mapea resultados a códigos sin distinguir firma, solución, formato o reutilización")
  void mapping() {
    assertThat(CaptchaErrorCode.from(VerificationOutcome.INVALID_SIGNATURE))
        .isEqualTo(CaptchaErrorCode.CAPTCHA_INVALID);
    assertThat(CaptchaErrorCode.from(VerificationOutcome.INVALID_SOLUTION))
        .isEqualTo(CaptchaErrorCode.CAPTCHA_INVALID);
    assertThat(CaptchaErrorCode.from(VerificationOutcome.INVALID_INPUT))
        .isEqualTo(CaptchaErrorCode.CAPTCHA_INVALID);
    assertThat(CaptchaErrorCode.from(VerificationOutcome.NOT_FOUND_OR_USED))
        .isEqualTo(CaptchaErrorCode.CAPTCHA_INVALID);
    assertThat(CaptchaErrorCode.from(VerificationOutcome.EXPIRED))
        .isEqualTo(CaptchaErrorCode.CAPTCHA_EXPIRED);
    assertThat(CaptchaErrorCode.from(VerificationOutcome.BLOCKED))
        .isEqualTo(CaptchaErrorCode.TOO_MANY_ATTEMPTS);
    assertThat(CaptchaErrorCode.from(VerificationOutcome.STORE_UNAVAILABLE))
        .isEqualTo(CaptchaErrorCode.CAPTCHA_UNAVAILABLE);
  }

  @Test
  @DisplayName("los 403 incluyen challengeUrl con context-path y headerName")
  void forbiddenIncludesDiscovery() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContextPath("/app");
    ProblemDetail p = problems.problem(CaptchaErrorCode.CAPTCHA_REQUIRED, request);
    assertThat(p.getStatus()).isEqualTo(403);
    assertThat(p.getType().toString()).isEqualTo("urn:captcha:error:captcha-required");
    assertThat(p.getDetail()).isEqualTo("req");
    assertThat(p.getProperties())
        .containsEntry("code", "CAPTCHA_REQUIRED")
        .containsEntry("challengeUrl", "/app/api/auth/challenge")
        .containsEntry("headerName", "X-Altcha");
  }

  @Test
  @DisplayName("429 lleva Retry-After en segundos enteros y no incluye challengeUrl")
  void tooManyAttempts() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    problems.write(
        CaptchaErrorCode.TOO_MANY_ATTEMPTS,
        new MockHttpServletRequest(),
        response,
        Duration.ofMillis(299_400));
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("300");
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString())
        .contains("\"code\":\"TOO_MANY_ATTEMPTS\"")
        .doesNotContain("challengeUrl");
  }
}
