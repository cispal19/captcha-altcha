package io.github.cispal19.captcha.integration.support;

import io.github.cispal19.captcha.application.service.AntiBotService;
import io.github.cispal19.captcha.application.service.LoginAttemptLimiter;
import io.github.cispal19.captcha.web.CaptchaVerificationException;
import io.github.cispal19.captcha.web.ClientOrigins;
import io.github.cispal19.captcha.web.security.BlockedLoginChecker;
import io.github.cispal19.captcha.web.security.CaptchaLockedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aplicación de prueba con Spring Security: sustituye a captcha-sample-app en las pruebas del
 * starter para evitar la dependencia circular entre módulos.
 */
@SpringBootApplication
public class TestSecurityApp {

  public static final String USER = "demo";
  public static final String PASSWORD = AltchaTestSupport.randomKey().substring(0, 20);

  @Bean
  SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(USER).password(encoder.encode(PASSWORD)).roles("USER").build());
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService users,
      PasswordEncoder encoder,
      BlockedLoginChecker blockedLoginChecker,
      ApplicationEventPublisher publisher) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    provider.setPreAuthenticationChecks(blockedLoginChecker);
    ProviderManager manager = new ProviderManager(provider);
    manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));
    return manager;
  }

  /** Endpoints de prueba: login, consulta pública, ruta no protegida y login explícito. */
  @RestController
  static class TestEndpoints {

    private final AuthenticationManager authenticationManager;
    private final AntiBotService antiBotService;
    private final LoginAttemptLimiter limiter;

    TestEndpoints(
        AuthenticationManager authenticationManager,
        AntiBotService antiBotService,
        LoginAttemptLimiter limiter) {
      this.authenticationManager = authenticationManager;
      this.antiBotService = antiBotService;
      this.limiter = limiter;
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
      return authenticate(body);
    }

    @PostMapping("/api/public/consulta/{id}")
    Map<String, String> consulta() {
      return Map.of("status", "ok");
    }

    @GetMapping({"/api/otro", "/api/public/consulta/{id}"})
    Map<String, String> otro() {
      return Map.of("status", "ok");
    }

    /** Integración explícita (filter.enabled=false), como propone el documento de arquitectura. */
    @PostMapping("/api/explicit/login")
    ResponseEntity<Map<String, String>> explicitLogin(
        @RequestBody Map<String, String> body, HttpServletRequest request) {
      String header = request.getHeader("X-Altcha");
      if (header == null || header.isBlank()) {
        throw CaptchaVerificationException.required();
      }
      CaptchaVerificationException.throwIfNotVerified(
          antiBotService.verify(header, ClientOrigins.of(request)));
      limiter.assertNotBlocked(body.get("username"), request.getRemoteAddr());
      return authenticate(body);
    }

    private ResponseEntity<Map<String, String>> authenticate(Map<String, String> body) {
      try {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                body.get("username"), body.get("password")));
        return ResponseEntity.ok(Map.of("status", "ok"));
      } catch (CaptchaLockedException e) {
        throw e;
      } catch (AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "denied"));
      }
    }
  }
}
