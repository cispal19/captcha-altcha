package io.github.cispal19.captcha.unit.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = io.github.cispal19.captcha.integration.support.TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.hmac-key=${random.value}${random.value}",
      "security.altcha.protected-paths=POST /api/auth/login,POST /api/public/consulta/**",
      "security.altcha.challenge-path=/api/seguridad/desafio",
      "security.altcha.header-name=X-Captcha"
    })
@AutoConfigureMockMvc
@DisplayName("Rutas protegidas adicionales y ruta/cabecera configurables")
class ProtectedPathsTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("POST a una ruta adicional sin cabecera → 403 con la ruta y cabecera configuradas")
  void additionalPath() throws Exception {
    mvc.perform(post("/api/public/consulta/123"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"))
        .andExpect(jsonPath("$.challengeUrl").value("/api/seguridad/desafio"))
        .andExpect(jsonPath("$.headerName").value("X-Captcha"));
  }

  @Test
  @DisplayName("GET a la misma ruta no se verifica; el desafío se expone en la ruta configurada")
  void otherMethodAndPath() throws Exception {
    mvc.perform(get("/api/public/consulta/123")).andExpect(status().isOk());
    mvc.perform(get("/api/seguridad/desafio")).andExpect(status().isOk());
  }
}
