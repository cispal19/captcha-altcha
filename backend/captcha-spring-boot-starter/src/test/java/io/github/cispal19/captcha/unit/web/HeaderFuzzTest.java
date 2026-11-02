package io.github.cispal19.captcha.unit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.max-attempts=100",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@DisplayName("Fuzzing de la cabecera X-Altcha: 403 sin trazas")
class HeaderFuzzTest {

  @Autowired MockMvc mvc;

  static Stream<String> malformed() {
    List<String> jsons =
        List.of(
            "{}",
            "null",
            "[]",
            "\"x\"",
            "{\"challenge\":null,\"solution\":null}",
            "{\"challenge\":{},\"solution\":{}}",
            "{\"challenge\":{\"parameters\":{}},\"solution\":{\"counter\":-1}}",
            "{\"challenge\":{\"parameters\":{\"algorithm\":\"PBKDF2/SHA-256\"},\"signature\":\"zz\"},\"solution\":{\"counter\":1,\"derivedKey\":\"00\"}}",
            "{\"challenge\":{\"parameters\":{\"algorithm\":\"SHA-1\",\"nonce\":\"a\",\"salt\":\"b\",\"cost\":1,\"keyLength\":1,\"keyPrefix\":\"0\"},\"signature\":\"ab\"},\"solution\":{\"counter\":99999999999999,\"derivedKey\":\"00\"}}",
            "{\"challenge\":{\"parameters\":{\"algorithm\":\"PBKDF2/SHA-256\",\"nonce\":\"a\",\"salt\":\"b\",\"cost\":1,\"keyLength\":32,\"keyPrefix\":\"0\"},\"signature\":\"ab\"},\"solution\":{\"counter\":1e309,\"derivedKey\":\"00\"}}",
            "{'challenge': 'x'}",
            "{\"challenge\":01}",
            "{\"a\":\"\\u0000\"}",
            "{\"a\":\"ñ😀\"}",
            "{\"challenge\":" + "[".repeat(500) + "]".repeat(500) + "}");
    List<String> values = new ArrayList<>();
    jsons.forEach(
        j -> values.add(Base64.getEncoder().encodeToString(j.getBytes(StandardCharsets.UTF_8))));
    values.addAll(
        List.of(
            "AAAA",
            "A===",
            "====",
            "abc",
            "%%%%",
            "null",
            "undefined",
            "../../etc/passwd",
            "eyJ",
            "e30",
            "e30=",
            "<script>alert(1)</script>",
            "' OR 1=1 --",
            "${jndi:ldap://x}",
            "A".repeat(4097),
            "A".repeat(4096),
            "ñandú==",
            "ZZZZ\u0000",
            "eyJhIjoxfQ==eyJhIjoxfQ==",
            "-",
            "+/+/",
            "////",
            "++++",
            "AAAAAAAA",
            "AA==AA==",
            "QUJD\nREVG",
            "0",
            "-1",
            "1e9",
            "true",
            "{}",
            "[]",
            "\"\"",
            "AAAA AAAA"));
    return values.stream();
  }

  @ParameterizedTest
  @MethodSource("malformed")
  @DisplayName("entrada malformada → 403 CAPTCHA_INVALID, sin trazas y en menos de 100 ms")
  void rejects(String header) throws Exception {
    long start = System.nanoTime();
    MvcResult result =
        mvc.perform(
                CaptchaFlows.login(CaptchaFlows.LOGIN_OK, "10.7.0.1").header("X-Altcha", header))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"))
            .andReturn();
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    String body = result.getResponse().getContentAsString();
    assertThat(body).doesNotContain("Exception").doesNotContain("\tat ").doesNotContain("org.");
    assertThat(elapsedMs).isLessThan(100);
  }
}
