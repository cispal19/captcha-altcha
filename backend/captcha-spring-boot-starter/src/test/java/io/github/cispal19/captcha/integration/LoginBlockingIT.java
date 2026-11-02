package io.github.cispal19.captcha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.RedisSupport;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.base-cost=1000",
      "security.altcha.key-prefix=it-login:",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@DisplayName("Bloqueo por usuario e IP con Spring Security y Redis")
class LoginBlockingIT {

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", RedisSupport::host);
    registry.add("spring.data.redis.port", RedisSupport::port);
  }

  @Autowired MockMvc mvc;
  @Autowired LoginAttemptLimiter limiter;

  private void badLogin(String ip) throws Exception {
    mvc.perform(
            CaptchaFlows.login(CaptchaFlows.LOGIN_BAD, ip)
                .header("X-Altcha", CaptchaFlows.solvedHeader(mvc, ip)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "5 contraseñas incorrectas desde varias IP → el usuario queda bloqueado desde otra IP (429)")
  void userBlockedAcrossIps() throws Exception {
    for (int i = 0; i < 5; i++) {
      badLogin("10.8.0." + (10 + i));
    }
    String otherIp = "10.8.1.1";
    mvc.perform(
            CaptchaFlows.login(CaptchaFlows.LOGIN_OK, otherIp)
                .header("X-Altcha", CaptchaFlows.solvedHeader(mvc, otherIp)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    limiter.reset(TestSecurityApp.USER);
  }

  @Test
  @DisplayName("un login exitoso reinicia el contador del usuario")
  void successResets() throws Exception {
    for (int i = 0; i < 3; i++) {
      badLogin("10.8.2." + i);
    }
    String ip = "10.8.2.50";
    mvc.perform(
            CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip)
                .header("X-Altcha", CaptchaFlows.solvedHeader(mvc, ip)))
        .andExpect(status().isOk());
    for (int i = 0; i < 4; i++) {
      badLogin("10.8.3." + i);
    }
    String last = "10.8.3.99";
    mvc.perform(
            CaptchaFlows.login(CaptchaFlows.LOGIN_OK, last)
                .header("X-Altcha", CaptchaFlows.solvedHeader(mvc, last)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "IP bloqueada por fallos de verificación → 429 en el login y costo adaptativo antes del bloqueo")
  void ipBlockedByCaptchaFailures() throws Exception {
    String ip = "10.8.4.4";
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip).header("X-Altcha", "AAAA"));
    String json = CaptchaFlows.challenge(mvc, ip);
    assertThat(new org.json.JSONObject(json).getJSONObject("parameters").getInt("cost"))
        .isEqualTo(2000);
    for (int i = 0; i < 4; i++) {
      mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip).header("X-Altcha", "AAAA"));
    }
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip).header("X-Altcha", "AAAA"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  @DisplayName("31 solicitudes de desafío en un minuto → la 31.ª responde 429")
  void challengeRateLimit() throws Exception {
    String ip = "10.8.5.5";
    for (int i = 0; i < 30; i++) {
      mvc.perform(
              get("/api/auth/challenge")
                  .with(
                      r -> {
                        r.setRemoteAddr(ip);
                        return r;
                      }))
          .andExpect(status().isOk());
    }
    mvc.perform(
            get("/api/auth/challenge")
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    }))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
  }
}
