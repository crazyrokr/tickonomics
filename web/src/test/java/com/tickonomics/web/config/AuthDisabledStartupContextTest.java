package com.tickonomics.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * Regression for the chaos-tests *Start services* step: the backend container runs with
 * {@code AUTH_DISABLED=true} and no OIDC issuer in the stack. This context reproduces that
 * situation - the OAuth2 auto-configurations are active and the configured issuer (port 9)
 * is dead - and checks that with the stub configuration the servlet web context still
 * refreshes and the stubs serve as the repository and decoder beans.
 */
class AuthDisabledStartupContextTest {

  private static final Map<String, Object> DEAD_ISSUER_PROPERTIES = Map.of(
      "security.auth-disabled", "true",
      "spring.security.oauth2.client.provider.keycloak.issuer-uri", "http://localhost:9",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri", "http://localhost:9");

  private static AnnotationConfigWebApplicationContext contextFor(Map<String, Object> properties, Class<?>... configs) {
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", new HashMap<>(properties)));
    for (Class<?> config : configs) {
      context.register(config);
    }
    return context;
  }

  @Test
  void givenAuthDisabledAndUnreachableIssuer_whenContextLoads_thenStubsServeRepositoryAndDecoder() {
    // GIVEN
    var context = contextFor(
        DEAD_ISSUER_PROPERTIES,
        AuthDisabledOauth2Stubs.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class);

    // WHEN
    context.refresh();

    // THEN
    try (context) {
      assertThat(context.getBean(ClientRegistrationRepository.class).findByRegistrationId("keycloak")).isNull();
      assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode("token"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("security.auth-disabled");
    }
  }
}
