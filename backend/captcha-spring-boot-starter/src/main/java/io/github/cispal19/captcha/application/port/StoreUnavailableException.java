package io.github.cispal19.captcha.application.port;

/** El almacén compartido no está disponible; se aplica fail-closed. */
public class StoreUnavailableException extends RuntimeException {

  public StoreUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
