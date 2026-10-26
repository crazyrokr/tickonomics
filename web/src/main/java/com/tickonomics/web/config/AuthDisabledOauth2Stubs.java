package com.tickonomics.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * While {@code security.auth-disabled=true}, Spring Boot's OAuth2 auto-configuration still
 * creates a {@link ClientRegistrationRepository} and a {@link JwtDecoder} bound to the
 * configured issuer ({@code KEYCLOAK_ISSUER_URI}, default {@code http://localhost:8080}).
 * In stacks without a live OIDC issuer (the chaos-tests backend, local dev without
 * Keycloak) those beans would point at a dead endpoint - any registration lookup or token
 * decode through them fails. These placeholders make the auto-configuration back off (both
 * auto-configured factories are {@code @ConditionalOnMissingBean}) and they are safe to
 * ignore because {@link SecurityConfig} builds an unsecured filter chain while auth is
 * disabled, so neither lookup nor decode is ever performed.
 */
@Configuration
public class AuthDisabledOauth2Stubs {

  @Bean
  @ConditionalOnProperty(name = "security.auth-disabled", havingValue = "true")
  ClientRegistrationRepository authDisabledClientRegistrationRepository() {
    return registrationId -> null;
  }

  @Bean
  @ConditionalOnProperty(name = "security.auth-disabled", havingValue = "true")
  JwtDecoder authDisabledJwtDecoder() {
    return token -> {
      throw new UnsupportedOperationException(
          "JWT decoding is unavailable while security.auth-disabled=true; "
              + "an unsecured filter chain is active");
    };
  }
}
