package io.github.cispal19.captcha.application.service;

import java.time.Duration;

/** Parámetros de límite de intentos y costo adaptativo. */
public record AttemptPolicy(
    int maxAttempts,
    Duration attemptWindow,
    Duration blockDuration,
    int baseCost,
    int maxCost,
    int challengeRateLimit) {}
