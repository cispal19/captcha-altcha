package io.github.cispal19.captcha.application.port;

import io.github.cispal19.captcha.domain.ChallengeId;

/** Solución decodificada; el adaptador conserva su representación interna. */
public interface ParsedSolution {

  ChallengeId challengeId();

  int cost();
}
