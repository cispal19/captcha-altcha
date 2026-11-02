package io.github.cispal19.captcha.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import java.net.URL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@DisplayName("Contrato HTTP: respuestas reales vs contracts/openapi.yaml")
class OpenApiContractIT {

  private static OpenApiInteractionValidator validator;

  @Autowired MockMvc mvc;

  @BeforeAll
  static void loadContract() {
    URL contract = OpenApiContractIT.class.getResource("/contracts/openapi.yaml");
    assertThat(contract).as("openapi.yaml del contrato").isNotNull();
    validator = OpenApiInteractionValidator.createForSpecificationUrl(contract.toString()).build();
  }

  private static void assertValid(
      String path, Request.Method method, MockHttpServletResponse response) throws Exception {
    SimpleResponse.Builder builder =
        SimpleResponse.Builder.status(response.getStatus()).withBody(response.getContentAsString());
    if (response.getContentType() != null) {
      builder.withContentType(response.getContentType());
    }
    for (String name : response.getHeaderNames()) {
      builder.withHeader(name, response.getHeaders(name));
    }
    ValidationReport report = validator.validateResponse(path, method, builder.build());
    assertThat(report.getMessages()).as(path + " " + response.getStatus()).isEmpty();
  }

  @Test
  @DisplayName("GET /api/auth/challenge 200 cumple el esquema Challenge")
  void challenge() throws Exception {
    assertValid(
        "/api/auth/challenge",
        Request.Method.GET,
        mvc.perform(get("/api/auth/challenge")).andReturn().getResponse());
  }

  @Test
  @DisplayName("POST /api/auth/login 403 cumple el esquema Problem (CAPTCHA_REQUIRED e INVALID)")
  void loginErrors() throws Exception {
    assertValid(
        "/api/auth/login",
        Request.Method.POST,
        mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.9.0.1"))
            .andReturn()
            .getResponse());
    assertValid(
        "/api/auth/login",
        Request.Method.POST,
        mvc.perform(
                CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.9.0.1").header("X-Altcha", "AAAA"))
            .andReturn()
            .getResponse());
  }
}
