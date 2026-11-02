package io.github.cispal19.captcha.sample;

import io.github.cispal19.captcha.web.security.BlockedLoginChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad de DEMOSTRACIÓN.
 *
 * <p>ADVERTENCIA: CSRF se desactiva solo porque el ejemplo expone una API JSON sin sesión. No copie
 * esta configuración a producción sin evaluar CSRF para su caso.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/**", "/api/seguridad/**", "/api/public/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(
      PasswordEncoder encoder, @Value("${SAMPLE_USER_PASSWORD}") String password) {
    return new InMemoryUserDetailsManager(
        User.withUsername("demo").password(encoder.encode(password)).roles("USER").build());
  }

  /** Una línea de integración: {@code setPreAuthenticationChecks(blockedLoginChecker)}. */
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
}
