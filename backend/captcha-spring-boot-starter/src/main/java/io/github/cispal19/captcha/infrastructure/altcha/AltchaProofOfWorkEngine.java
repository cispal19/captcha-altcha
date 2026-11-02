package io.github.cispal19.captcha.infrastructure.altcha;

import io.github.cispal19.captcha.application.port.InvalidSolutionException;
import io.github.cispal19.captcha.application.port.IssuedChallenge;
import io.github.cispal19.captcha.application.port.ParsedSolution;
import io.github.cispal19.captcha.application.port.ProofOfWorkEngine;
import io.github.cispal19.captcha.domain.ChallengeId;
import io.github.cispal19.captcha.domain.RawSolution;
import io.github.cispal19.captcha.domain.VerificationOutcome;
import java.time.Duration;
import java.util.regex.Pattern;
import org.altcha.altcha.v2.Altcha;

/**
 * Adaptador de la librería oficial ALTCHA PoW v2 ({@code org.altcha.altcha.v2.Altcha}). No usa el
 * modo determinista y firma con la subclave {@code captcha-challenge}.
 */
public class AltchaProofOfWorkEngine implements ProofOfWorkEngine {

  private static final long MAX_UINT32 = 0xFFFF_FFFFL;
  private static final Pattern HEX = Pattern.compile("^[0-9a-f]+$");

  private final String challengeSecret;
  private final String algorithm;
  private final Altcha.KeyDerivationFunction kdf;

  public AltchaProofOfWorkEngine(String challengeSecret, String algorithm) {
    this.challengeSecret = challengeSecret;
    this.algorithm = algorithm;
    this.kdf = Altcha.kdf(algorithm);
  }

  @Override
  public IssuedChallenge create(int cost, Duration ttl) {
    try {
      Altcha.Challenge challenge =
          Altcha.createChallenge(
              new Altcha.CreateChallengeOptions()
                  .algorithm(algorithm)
                  .cost(cost)
                  .counterMode(Altcha.CounterMode.UINT32)
                  .hmacSignatureSecret(challengeSecret)
                  .expiresInSeconds(ttl.toSeconds()));
      return new IssuedChallenge(
          ChallengeId.fromSignature(challenge.signature()), challenge.toJson(), cost);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo crear el desafío ALTCHA", e);
    }
  }

  @Override
  public ParsedSolution parse(RawSolution raw) {
    Altcha.Payload payload;
    try {
      payload = Altcha.parsePayload(raw.value());
    } catch (Exception e) {
      throw new InvalidSolutionException("Payload ALTCHA no decodificable", e);
    }
    if (payload == null || payload.challenge() == null || payload.solution() == null) {
      throw new InvalidSolutionException("Payload incompleto");
    }
    Altcha.Challenge challenge = payload.challenge();
    Altcha.ChallengeParameters params = challenge.parameters();
    Altcha.Solution solution = payload.solution();
    if (params == null || !isHex(challenge.signature())) {
      throw new InvalidSolutionException("Desafío sin parámetros o firma");
    }
    if (!algorithm.equals(params.algorithm())) {
      throw new InvalidSolutionException("Algoritmo no permitido");
    }
    if (params.keySignature() != null) {
      throw new InvalidSolutionException("Modo determinista no permitido");
    }
    if (solution.counter() < 0 || solution.counter() > MAX_UINT32) {
      throw new InvalidSolutionException("Contador fuera de rango UINT32");
    }
    String derivedKey = solution.derivedKey();
    if (!isHex(derivedKey) || derivedKey.length() != params.keyLength() * 2) {
      throw new InvalidSolutionException("derivedKey con longitud o formato inválido");
    }
    return new AltchaParsedSolution(
        ChallengeId.fromSignature(challenge.signature()), params.cost(), payload);
  }

  @Override
  public VerificationOutcome verify(ParsedSolution parsed) {
    if (!(parsed instanceof AltchaParsedSolution altcha)) {
      throw new IllegalArgumentException("Solución no producida por este motor");
    }
    try {
      Altcha.VerifySolutionResult result =
          Altcha.verifySolution(
              altcha.payload().challenge(), altcha.payload().solution(), challengeSecret, kdf);
      if (result.verified()) {
        return VerificationOutcome.VERIFIED;
      }
      if (Boolean.TRUE.equals(result.invalidSignature())) {
        return VerificationOutcome.INVALID_SIGNATURE;
      }
      if (result.expired()) {
        return VerificationOutcome.EXPIRED;
      }
      return VerificationOutcome.INVALID_SOLUTION;
    } catch (Exception e) {
      return VerificationOutcome.INVALID_SOLUTION;
    }
  }

  private static boolean isHex(String value) {
    return value != null && !value.isEmpty() && HEX.matcher(value).matches();
  }

  private record AltchaParsedSolution(ChallengeId challengeId, int cost, Altcha.Payload payload)
      implements ParsedSolution {}
}
