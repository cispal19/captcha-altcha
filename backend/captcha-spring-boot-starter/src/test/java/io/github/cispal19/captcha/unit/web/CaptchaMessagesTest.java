package io.github.cispal19.captcha.unit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.cispal19.captcha.autoconfigure.CaptchaProperties;
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
      "security.altcha.hmac-key=${random.value}${random.value}",
      "security.altcha.messages.invalid=Verificación fallida",
      "security.altcha.messages.required=Falta la verificación"
    })
@AutoConfigureMockMvc
@DisplayName("Mensajes de error configurables")
class CaptchaMessagesTest {

  @Autowired MockMvc mvc;
  @Autowired CaptchaProperties properties;

  @Test
  @DisplayName("el detail usa los textos configurados")
  void custom() throws Exception {
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.4.0.1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Falta la verificación"));
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.4.0.1").header("X-Altcha", "AAAA"))
        .andExpect(jsonPath("$.detail").value("Verificación fallida"));
  }

  @Test
  @DisplayName("los no configurados conservan el texto por defecto en español")
  void defaults() {
    assertThat(properties.messages().expired())
        .isEqualTo("La verificación expiró. Inténtelo nuevamente.");
  }
}
