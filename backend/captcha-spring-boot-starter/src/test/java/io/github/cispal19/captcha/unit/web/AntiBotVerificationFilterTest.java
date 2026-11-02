package io.github.cispal19.captcha.unit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.base-cost=1000",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@DisplayName("Filtro de verificación y endpoint del desafío")
class AntiBotVerificationFilterTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("GET /api/auth/challenge → 200 JSON con parámetros PoW v2 y Cache-Control no-store")
  void challengeEndpoint() throws Exception {
    MvcResult result =
        mvc.perform(get("/api/auth/challenge"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andReturn();
    JSONObject json = new JSONObject(result.getResponse().getContentAsString());
    JSONObject p = json.getJSONObject("parameters");
    assertThat(p.keySet())
        .contains("algorithm", "nonce", "salt", "cost", "keyLength", "keyPrefix", "expiresAt");
    assertThat(json.getString("signature")).matches("[0-9a-f]+");
  }

  @Test
  @DisplayName("sin cabecera → 403 CAPTCHA_REQUIRED con challengeUrl y headerName")
  void missingHeader() throws Exception {
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.1.0.1"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"))
        .andExpect(jsonPath("$.challengeUrl").value("/api/auth/challenge"))
        .andExpect(jsonPath("$.headerName").value("X-Altcha"));
  }

  @Test
  @DisplayName("solución válida → el login continúa (200) y reutilizarla → 403 CAPTCHA_INVALID")
  void validThenReplay() throws Exception {
    String header = CaptchaFlows.solvedHeader(mvc, "10.1.0.2");
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.1.0.2").header("X-Altcha", header))
        .andExpect(status().isOk());
    MvcResult replay =
        mvc.perform(
                CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.1.0.2").header("X-Altcha", header))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"))
            .andReturn();
    String body = replay.getResponse().getContentAsString();
    assertThat(body).doesNotContain("Exception").doesNotContain("\tat ").doesNotContain(header);
  }

  @Test
  @DisplayName("solución alterada → 403 CAPTCHA_INVALID")
  void tampered() throws Exception {
    JSONObject payload = AltchaTestSupport.decode(CaptchaFlows.solvedHeader(mvc, "10.1.0.3"));
    payload
        .getJSONObject("solution")
        .put("counter", payload.getJSONObject("solution").getLong("counter") + 1);
    String header =
        AltchaTestSupport.encode(
            payload.getJSONObject("challenge"),
            payload.getJSONObject("solution").getLong("counter"),
            payload.getJSONObject("solution").getString("derivedKey"));
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.1.0.3").header("X-Altcha", header))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
  }

  @Test
  @DisplayName("ruta no protegida → sin verificación")
  void unprotected() throws Exception {
    mvc.perform(get("/api/otro")).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "el filtro corre antes de Spring Security: credenciales nunca evaluadas sin cabecera")
  void runsBeforeAuthentication() throws Exception {
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_BAD, "10.1.0.4"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
  }
}
