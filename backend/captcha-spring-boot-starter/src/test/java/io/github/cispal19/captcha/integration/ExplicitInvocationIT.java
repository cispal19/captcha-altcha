package io.github.cispal19.captcha.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.base-cost=1000",
      "security.altcha.filter.enabled=false",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@DisplayName("Integración explícita desde el servicio de autenticación")
class ExplicitInvocationIT {

  @Autowired MockMvc mvc;

  private static MockHttpServletRequestBuilder explicitLogin(String ip) {
    return post("/api/explicit/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(CaptchaFlows.LOGIN_OK)
        .with(
            r -> {
              r.setRemoteAddr(ip);
              return r;
            });
  }

  @Test
  @DisplayName(
      "sin cabecera → mismo 403 CAPTCHA_REQUIRED que el filtro, con challengeUrl y headerName")
  void required() throws Exception {
    mvc.perform(explicitLogin("10.5.0.1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"))
        .andExpect(jsonPath("$.challengeUrl").value("/api/auth/challenge"))
        .andExpect(jsonPath("$.headerName").value("X-Altcha"));
  }

  @Test
  @DisplayName("válida → 200; reutilizada → 403 CAPTCHA_INVALID")
  void validAndReplay() throws Exception {
    String header = CaptchaFlows.solvedHeader(mvc, "10.5.0.2");
    mvc.perform(explicitLogin("10.5.0.2").header("X-Altcha", header)).andExpect(status().isOk());
    mvc.perform(explicitLogin("10.5.0.2").header("X-Altcha", header))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"))
        .andExpect(jsonPath("$.headerName").value("X-Altcha"));
  }

  @Test
  @DisplayName("el login normal no está protegido cuando el filtro está desactivado")
  void filterOff() throws Exception {
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.5.0.3")).andExpect(status().isOk());
  }
}
