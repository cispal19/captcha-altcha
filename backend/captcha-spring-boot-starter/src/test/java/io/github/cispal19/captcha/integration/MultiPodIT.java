package io.github.cispal19.captcha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cispal19.captcha.integration.support.AltchaTestSupport;
import io.github.cispal19.captcha.integration.support.CaptchaFlows;
import io.github.cispal19.captcha.integration.support.RedisSupport;
import io.github.cispal19.captcha.integration.support.TestSecurityApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@DisplayName("Multi-pod: dos instancias comparten Redis y clave; consumo único global")
class MultiPodIT {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static ConfigurableApplicationContext podA;
  private static ConfigurableApplicationContext podB;

  @BeforeAll
  static void start() {
    String key = AltchaTestSupport.randomKey();
    podA = pod(key);
    podB = pod(key);
  }

  @AfterAll
  static void stop() {
    podA.close();
    podB.close();
  }

  private static ConfigurableApplicationContext pod(String key) {
    return new SpringApplicationBuilder(TestSecurityApp.class)
        .properties(
            "server.port=0",
            "security.altcha.hmac-key=" + key,
            "security.altcha.base-cost=1000",
            "security.altcha.max-attempts=100",
            "security.altcha.challenge-rate-limit=1000",
            "security.altcha.key-prefix=it-pods:",
            "spring.data.redis.host=" + RedisSupport.host(),
            "spring.data.redis.port=" + RedisSupport.port())
        .run();
  }

  private static String base(ConfigurableApplicationContext ctx) {
    int port = ((WebServerApplicationContext) ctx).getWebServer().getPort();
    return "http://localhost:" + port;
  }

  private static int login(ConfigurableApplicationContext pod, String header) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(base(pod) + "/api/auth/login"))
            .header("Content-Type", "application/json")
            .header("X-Altcha", header)
            .POST(HttpRequest.BodyPublishers.ofString(CaptchaFlows.LOGIN_OK))
            .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private static String solvedFrom(ConfigurableApplicationContext pod) throws Exception {
    String json =
        HTTP.send(
                HttpRequest.newBuilder(URI.create(base(pod) + "/api/auth/challenge")).build(),
                HttpResponse.BodyHandlers.ofString())
            .body();
    return AltchaTestSupport.solve(json);
  }

  @Test
  @DisplayName("emitido en A, verificado en B → aceptado; repetido en A → rechazado")
  void crossPod() throws Exception {
    String header = solvedFrom(podA);
    assertThat(login(podB, header)).isEqualTo(200);
    assertThat(login(podA, header)).isEqualTo(403);
  }

  @Test
  @DisplayName(
      "100 verificaciones concurrentes del mismo payload alternando A y B → exactamente 1 aceptada")
  void concurrentAcrossPods() throws Exception {
    String header = solvedFrom(podA);
    ExecutorService pool = Executors.newFixedThreadPool(32);
    try {
      List<Callable<Integer>> calls =
          IntStream.range(0, 100)
              .<Callable<Integer>>mapToObj(i -> () -> login(i % 2 == 0 ? podA : podB, header))
              .toList();
      long accepted =
          pool.invokeAll(calls).stream()
              .mapToInt(
                  f -> {
                    try {
                      return f.get();
                    } catch (Exception e) {
                      throw new IllegalStateException(e);
                    }
                  })
              .filter(code -> code == 200)
              .count();
      assertThat(accepted).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
