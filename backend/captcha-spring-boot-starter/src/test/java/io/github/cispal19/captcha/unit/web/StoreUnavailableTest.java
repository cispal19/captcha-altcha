package io.github.cispal19.captcha.unit.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.cispal19.captcha.application.port.ChallengeStore;
import io.github.cispal19.captcha.application.port.StoreUnavailableException;
import io.github.cispal19.captcha.domain.ChallengeId;
import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.base-cost=1000",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@Import(StoreUnavailableTest.FailingStore.class)
@DisplayName("Almacén caído → 503 CAPTCHA_UNAVAILABLE (fail-closed)")
class StoreUnavailableTest {

  @TestConfiguration
  static class FailingStore {
    @Bean
    ChallengeStore failingChallengeStore() {
      return new ChallengeStore() {
        @Override
        public void register(ChallengeId id, Duration ttl) {
          throw new StoreUnavailableException("down", null);
        }

        @Override
        public boolean consume(ChallengeId id) {
          throw new StoreUnavailableException("down", null);
        }
      };
    }
  }

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("emitir y verificar responden 503 sin trazas")
  void unavailable() throws Exception {
    mvc.perform(get("/api/auth/challenge"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CAPTCHA_UNAVAILABLE"));
    String header =
        AltchaTestSupport.encode(
            new org.json.JSONObject()
                .put(
                    "parameters",
                    new org.json.JSONObject()
                        .put("algorithm", "PBKDF2/SHA-256")
                        .put("nonce", "00")
                        .put("salt", "00")
                        .put("cost", 1000)
                        .put("keyLength", 32)
                        .put("keyPrefix", "00"))
                .put("signature", "ab"),
            1,
            "00".repeat(32));
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.2.0.1").header("X-Altcha", header))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CAPTCHA_UNAVAILABLE"));
  }
}
