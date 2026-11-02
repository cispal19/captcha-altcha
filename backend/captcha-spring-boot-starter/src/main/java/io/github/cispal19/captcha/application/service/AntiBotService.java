package io.github.cispal19.captcha.application.service;

import io.github.cispal19.captcha.application.port.ChallengeStore;
import io.github.cispal19.captcha.application.port.InvalidSolutionException;
import io.github.cispal19.captcha.application.port.IssuedChallenge;
import io.github.cispal19.captcha.application.port.ParsedSolution;
import io.github.cispal19.captcha.application.port.ProofOfWorkEngine;
import io.github.cispal19.captcha.application.port.StoreUnavailableException;
import io.github.cispal19.captcha.application.port.SubjectHasher;
import io.github.cispal19.captcha.application.port.VerificationEventLogger;
import io.github.cispal19.captcha.domain.Origin;
import io.github.cispal19.captcha.domain.RawSolution;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Duration;
import java.util.Optional;

/**
 * Emite desafíos y verifica soluciones. Orden de verificación: bloqueo de IP → formato → consumo
 * atómico → verificación de la prueba (el desafío se invalida aunque la solución falle).
 */
public class AntiBotService {

  private final ProofOfWorkEngine engine;
  private final ChallengeStore store;
  private final LoginAttemptLimiter limiter;
  private final SubjectHasher hasher;
  private final VerificationEventLogger logger;
  private final Duration challengeTtl;

  public AntiBotService(
      ProofOfWorkEngine engine,
      ChallengeStore store,
      LoginAttemptLimiter limiter,
      SubjectHasher hasher,
      VerificationEventLogger logger,
      Duration challengeTtl) {
    this.engine = engine;
    this.store = store;
    this.limiter = limiter;
    this.hasher = hasher;
    this.logger = logger;
    this.challengeTtl = challengeTtl;
  }

  /**
   * Emite un desafío con costo adaptativo y lo registra para su consumo único.
   *
   * @throws LoginBlockedException si la IP supera el límite de solicitudes
   * @throws StoreUnavailableException si el almacén no está disponible
   */
  public IssuedChallenge issue(Origin origin) {
    limiter.checkChallengeRate(origin.ip());
    IssuedChallenge challenge = engine.create(limiter.costFor(origin.ip()), challengeTtl);
    store.register(challenge.id(), challengeTtl);
    return challenge;
  }

  /** Verifica la solución recibida en la cabecera; nunca lanza excepciones de validación. */
  public VerificationResult verify(String header, Origin origin) {
    String subject = hasher.hash(origin.ip());
    int cost = 0;
    try {
      Optional<Duration> blocked = limiter.ipBlockedFor(origin.ip());
      if (blocked.isPresent()) {
        logger.verification(VerificationOutcome.BLOCKED, cost, subject);
        return new VerificationResult(VerificationOutcome.BLOCKED, blocked);
      }
      Optional<RawSolution> raw = RawSolution.from(header);
      if (raw.isEmpty()) {
        return reject(VerificationOutcome.INVALID_INPUT, cost, origin, subject);
      }
      ParsedSolution parsed;
      try {
        parsed = engine.parse(raw.get());
      } catch (InvalidSolutionException e) {
        return reject(VerificationOutcome.INVALID_INPUT, cost, origin, subject);
      }
      cost = parsed.cost();
      if (!store.consume(parsed.challengeId())) {
        return reject(VerificationOutcome.NOT_FOUND_OR_USED, cost, origin, subject);
      }
      VerificationOutcome outcome = engine.verify(parsed);
      if (outcome != VerificationOutcome.VERIFIED) {
        return reject(outcome, cost, origin, subject);
      }
      logger.verification(outcome, cost, subject);
      return VerificationResult.of(outcome);
    } catch (StoreUnavailableException e) {
      logger.verification(VerificationOutcome.STORE_UNAVAILABLE, cost, subject);
      return VerificationResult.of(VerificationOutcome.STORE_UNAVAILABLE);
    }
  }

  private VerificationResult reject(
      VerificationOutcome outcome, int cost, Origin origin, String subject) {
    limiter.recordIpFailure(origin.ip());
    logger.verification(outcome, cost, subject);
    return VerificationResult.of(outcome);
  }
}
