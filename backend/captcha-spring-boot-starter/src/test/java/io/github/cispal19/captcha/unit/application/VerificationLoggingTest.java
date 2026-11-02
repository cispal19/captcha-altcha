package io.github.cispal19.captcha.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.cispal19.captcha.autoconfigure.CaptchaProperties;
import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestSecurityApp.class,
    properties = {
      "security.altcha.store=memory",
      "security.altcha.base-cost=1000",
      "security.altcha.max-attempts=3",
      "security.altcha.hmac-key=${random.value}${random.value}"
    })
@AutoConfigureMockMvc
@DisplayName("Eventos de auditoría sin payload, clave, IP ni usuario en claro")
class VerificationLoggingTest {

  @Autowired MockMvc mvc;
  @Autowired CaptchaProperties properties;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final Logger audit = (Logger) LoggerFactory.getLogger("captcha.audit");

  @BeforeEach
  void attach() {
    appender.start();
    audit.addAppender(appender);
  }

  @AfterEach
  void detach() {
    audit.detachAppender(appender);
  }

  @Test
  @DisplayName("captcha.verification y captcha.block con outcome, cost, subjectHash y ts")
  void auditEvents() throws Exception {
    String ip = "10.6.0.77";
    String header = CaptchaFlows.solvedHeader(mvc, ip);
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip).header("X-Altcha", header));
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip).header("X-Altcha", header));
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip).header("X-Altcha", "AAAA"));
    mvc.perform(CaptchaFlows.login(CaptchaFlows.LOGIN_OK, ip).header("X-Altcha", "AAAA"));
    String logs =
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
    assertThat(logs)
        .contains("captcha.verification outcome=VERIFIED cost=1000 subjectHash=")
        .contains("outcome=NOT_FOUND_OR_USED")
        .contains("captcha.block dimension=IP")
        .contains("ts=");
    assertThat(logs)
        .doesNotContain(header)
        .doesNotContain(ip)
        .doesNotContain(properties.hmacKey())
        .doesNotContain(TestSecurityApp.USER);
  }
}
