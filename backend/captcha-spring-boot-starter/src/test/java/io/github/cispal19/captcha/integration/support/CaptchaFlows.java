package io.github.cispal19.captcha.integration.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Flujos de prueba: obtener un desafío, resolverlo y llamar al login. */
public final class CaptchaFlows {

  public static final String LOGIN_OK =
      "{\"username\":\""
          + TestSecurityApp.USER
          + "\",\"password\":\""
          + TestSecurityApp.PASSWORD
          + "\"}";
  public static final String LOGIN_BAD =
      "{\"username\":\"" + TestSecurityApp.USER + "\",\"password\":\"incorrecta\"}";

  private CaptchaFlows() {}

  public static String challenge(MockMvc mvc, String ip) throws Exception {
    return mvc.perform(get("/api/auth/challenge").with(r -> withIp(r, ip)))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  public static String solvedHeader(MockMvc mvc, String ip) throws Exception {
    return AltchaTestSupport.solve(challenge(mvc, ip));
  }

  public static MockHttpServletRequestBuilder login(String body, String ip) {
    return post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .with(r -> withIp(r, ip));
  }

  private static org.springframework.mock.web.MockHttpServletRequest withIp(
      org.springframework.mock.web.MockHttpServletRequest request, String ip) {
    request.setRemoteAddr(ip);
    return request;
  }
}
