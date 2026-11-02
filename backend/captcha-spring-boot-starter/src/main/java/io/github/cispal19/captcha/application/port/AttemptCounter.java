package io.github.cispal19.captcha.application.port;

import io.github.cispal19.captcha.domain.AttemptKey;
import java.time.Duration;
import java.util.Optional;

/** Contadores de intentos y bloqueos temporales, compartidos entre instancias. */
public interface AttemptCounter {

  /**
   * Registra un fallo dentro de la ventana; al alcanzar {@code maxAttempts} bloquea durante {@code
   * blockDuration} y reinicia el contador.
   *
   * @return fallos acumulados tras registrar este (0 si se acaba de bloquear)
   */
  long recordFailure(AttemptKey key, Duration window, int maxAttempts, Duration blockDuration);

  void reset(AttemptKey key);

  long failures(AttemptKey key);

  Optional<Duration> blockedFor(AttemptKey key);

  /** Incrementa un contador de solicitudes con ventana fija y devuelve el valor actual. */
  long countRequest(AttemptKey key, Duration window);
}
