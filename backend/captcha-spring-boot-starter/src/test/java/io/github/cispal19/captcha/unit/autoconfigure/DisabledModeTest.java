package io.github.cispal19.captcha.unit.autoconfigure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.enabled=false",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@DisplayName("Librería deshabilitada fuera de producción")
class DisabledModeTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("sin endpoint de desafío, login sin cabecera llega al controlador")
  void disabled() throws Exception {
    mvc.perform(get("/api/auth/challenge")).andExpect(status().isNotFound());
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.3.0.1")).andExpect(status().isOk());
  }
}
