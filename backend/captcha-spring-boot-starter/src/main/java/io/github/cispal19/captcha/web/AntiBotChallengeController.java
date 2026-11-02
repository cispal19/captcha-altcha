package io.github.cispal19.captcha.web;

import io.github.cispal19.captcha.application.port.IssuedChallenge;
import io.github.cispal19.captcha.application.service.AntiBotService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expone {@code GET ${security.altcha.challenge-path}}. */
@RestController
public class AntiBotChallengeController {

  private final AntiBotService service;

  public AntiBotChallengeController(AntiBotService service) {
    this.service = service;
  }

  @GetMapping(
      path = "${security.altcha.challenge-path:/api/auth/challenge}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> challenge(HttpServletRequest request) {
    IssuedChallenge challenge = service.issue(ClientOrigins.of(request));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_JSON)
        .body(challenge.json());
  }
}
