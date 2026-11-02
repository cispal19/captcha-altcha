package io.github.cispal19.captcha.application.port;

/** El payload recibido no tiene una estructura válida. */
public class InvalidSolutionException extends RuntimeException {

  public InvalidSolutionException(String message) {
    super(message);
  }

  public InvalidSolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
