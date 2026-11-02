package io.github.cispal19.captcha.sample;

import io.github.cispal19.captcha.web.security.CaptchaLockedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Login existente del proyecto: no conoce la librería; el filtro verifica antes de llegar aquí. */
@RestController
public class LoginController {

  record LoginRequest(String username, String password) {}

  private final AuthenticationManager authenticationManager;

  public LoginController(AuthenticationManager authenticationManager) {
    this.authenticationManager = authenticationManager;
  }

  @PostMapping("/api/auth/login")
  public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request.username(), request.password()));
      return ResponseEntity.ok(Map.of("status", "ok", "message", "Ingreso correcto"));
    } catch (CaptchaLockedException e) {
      throw e;
    } catch (AuthenticationException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("status", "denied", "message", "Usuario o contraseña incorrectos"));
    }
  }
}
