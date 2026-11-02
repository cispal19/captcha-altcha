package io.github.cispal19.captcha.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.cispal19.captcha.application.port.ChallengeStore;
import io.github.cispal19.captcha.application.port.InvalidSolutionException;
import io.github.cispal19.captcha.application.port.IssuedChallenge;
import io.github.cispal19.captcha.application.port.ParsedSolution;
import io.github.cispal19.captcha.application.port.ProofOfWorkEngine;
import io.github.cispal19.captcha.application.port.StoreUnavailableException;
import io.github.cispal19.captcha.application.port.VerificationEventLogger;
import io.github.cispal19.captcha.application.service.AntiBotService;
import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.application.service.VerificationResult;
import io.github.cispal19.captcha.domain.ChallengeId;
import io.github.cispal19.captcha.domain.Origin;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@DisplayName("AntiBotService: emisión y verificación")
class AntiBotServiceTest {

  private static final String VALID_HEADER = "eyJhIjoxfQ==";
  private static final ChallengeId ID = ChallengeId.fromSignature("sig");
  private static final Origin ORIGIN = Origin.of("10.0.0.1");
  private static final Duration TTL = Duration.ofSeconds(120);

  private ProofOfWorkEngine engine;
  private ChallengeStore store;
  private LoginAttemptLimiter limiter;
  private VerificationEventLogger logger;
  private AntiBotService service;
  private final ParsedSolution parsed =
      new ParsedSolution() {
        @Override
        public ChallengeId challengeId() {
          return ID;
        }

        @Override
        public int cost() {
          return 5000;
        }
      };

  @BeforeEach
  void setUp() {
    engine = mock(ProofOfWorkEngine.class);
    store = mock(ChallengeStore.class);
    limiter = mock(LoginAttemptLimiter.class);
    logger = mock(VerificationEventLogger.class);
    when(limiter.ipBlockedFor(any())).thenReturn(Optional.empty());
    when(limiter.costFor(any())).thenReturn(5000);
    service = new AntiBotService(engine, store, limiter, v -> "hash", logger, TTL);
  }

  @Test
  @DisplayName("issue registra el id con challenge-ttl y usa el costo adaptativo")
  void issueRegisters() {
    when(limiter.costFor("10.0.0.1")).thenReturn(10000);
    when(engine.create(10000, TTL)).thenReturn(new IssuedChallenge(ID, "{}", 10000));
    IssuedChallenge issued = service.issue(ORIGIN);
    assertThat(issued.json()).isEqualTo("{}");
    verify(limiter).checkChallengeRate("10.0.0.1");
    verify(store).register(ID, TTL);
  }

  @Test
  @DisplayName("cabecera inválida → INVALID_INPUT sin tocar el store ni el motor")
  void invalidHeader() {
    VerificationResult r = service.verify("no base64 !", ORIGIN);
    assertThat(r.outcome()).isEqualTo(VerificationOutcome.INVALID_INPUT);
    verifyNoInteractions(store, engine);
    verify(limiter).recordIpFailure("10.0.0.1");
  }

  @Test
  @DisplayName("payload estructuralmente inválido → INVALID_INPUT sin consumir")
  void unparsable() {
    when(engine.parse(any())).thenThrow(new InvalidSolutionException("x"));
    assertThat(service.verify(VALID_HEADER, ORIGIN).outcome())
        .isEqualTo(VerificationOutcome.INVALID_INPUT);
    verifyNoInteractions(store);
  }

  @Test
  @DisplayName("consume=false → NOT_FOUND_OR_USED sin verificar")
  void notFoundOrUsed() {
    when(engine.parse(any())).thenReturn(parsed);
    when(store.consume(ID)).thenReturn(false);
    assertThat(service.verify(VALID_HEADER, ORIGIN).outcome())
        .isEqualTo(VerificationOutcome.NOT_FOUND_OR_USED);
    verify(engine, never()).verify(any());
    verify(limiter).recordIpFailure("10.0.0.1");
  }

  @Test
  @DisplayName("consume antes de verificar y devuelve el resultado del motor")
  void consumesThenVerifies() {
    when(engine.parse(any())).thenReturn(parsed);
    when(store.consume(ID)).thenReturn(true);
    when(engine.verify(parsed)).thenReturn(VerificationOutcome.INVALID_SOLUTION);
    assertThat(service.verify(VALID_HEADER, ORIGIN).outcome())
        .isEqualTo(VerificationOutcome.INVALID_SOLUTION);
    InOrder order = inOrder(store, engine);
    order.verify(store).consume(ID);
    order.verify(engine).verify(parsed);
    verify(limiter).recordIpFailure("10.0.0.1");
  }

  @Test
  @DisplayName("VERIFIED no registra fallo y emite el evento de auditoría")
  void verified() {
    when(engine.parse(any())).thenReturn(parsed);
    when(store.consume(ID)).thenReturn(true);
    when(engine.verify(parsed)).thenReturn(VerificationOutcome.VERIFIED);
    VerificationResult r = service.verify(VALID_HEADER, ORIGIN);
    assertThat(r.isVerified()).isTrue();
    verify(limiter, never()).recordIpFailure(any());
    verify(logger).verification(VerificationOutcome.VERIFIED, 5000, "hash");
  }

  @Test
  @DisplayName("IP bloqueada → BLOCKED con retryAfter, sin consumir")
  void blocked() {
    when(limiter.ipBlockedFor("10.0.0.1")).thenReturn(Optional.of(Duration.ofSeconds(90)));
    VerificationResult r = service.verify(VALID_HEADER, ORIGIN);
    assertThat(r.outcome()).isEqualTo(VerificationOutcome.BLOCKED);
    assertThat(r.retryAfter()).contains(Duration.ofSeconds(90));
    verifyNoInteractions(store);
  }

  @Test
  @DisplayName("almacén caído → STORE_UNAVAILABLE (fail-closed)")
  void storeUnavailable() {
    when(engine.parse(any())).thenReturn(parsed);
    when(store.consume(ID)).thenThrow(new StoreUnavailableException("down", null));
    assertThat(service.verify(VALID_HEADER, ORIGIN).outcome())
        .isEqualTo(VerificationOutcome.STORE_UNAVAILABLE);
    verify(logger).verification(eq(VerificationOutcome.STORE_UNAVAILABLE), anyInt(), eq("hash"));
  }
}
