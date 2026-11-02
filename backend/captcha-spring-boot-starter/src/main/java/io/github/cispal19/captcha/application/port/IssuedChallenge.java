package io.github.cispal19.captcha.application.port;

import io.github.cispal19.captcha.domain.ChallengeId;

/** Desafío emitido: identificador, JSON para el cliente y costo aplicado. */
public record IssuedChallenge(ChallengeId id, String json, int cost) {}
