package io.github.cispal19.captcha.application.port;

import io.github.cispal19.captcha.domain.RawSolution;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Duration;

/** Motor de prueba de trabajo: crea desafíos firmados y verifica soluciones. */
public interface ProofOfWorkEngine {

  IssuedChallenge create(int cost, Duration ttl);

  /**
   * Decodifica y valida la estructura del payload sin verificar la solución.
   *
   * @throws InvalidSolutionException si el payload no es estructuralmente válido
   */
  ParsedSolution parse(RawSolution raw);

  /** Verifica firma, expiración y solución de un payload ya decodificado. */
  VerificationOutcome verify(ParsedSolution solution);
}
