package com.tickonomics.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AuthDisabledOauth2StubsTest {

  private AnnotationConfigApplicationContext contextWith(String authDisabled) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("security.auth-disabled", authDisabled);
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
    context.register(AuthDisabledOauth2Stubs.class);
    context.refresh();
    return context;
  }

  @Nested
  class GivenAuthDisabled {

    @Test
    void whenContextLoads_thenStubBeansExist() {
      // WHEN
      try (var context = contextWith("true")) {
        // THEN
        assertThat(context.getBean(ClientRegistrationRepository.class)).isNotNull();
        assertThat(context.getBean(JwtDecoder.class)).isNotNull();
      }
    }

    @Test
    void givenStubRepository_whenLookup_thenNoRegistration() {
      // WHEN
      try (var context = contextWith("true")) {
        // THEN
        assertThat(context.getBean(ClientRegistrationRepository.class).findByRegistrationId("any"))
            .isNull();
      }
    }

    @Test
    void givenStubDecoder_whenDecode_thenUnavailable() {
      // WHEN
      try (var context = contextWith("true")) {
        // THEN
        assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode("token"))
            .isInstanceOf(UnsupportedOperationException.class);
      }
    }
  }

  @Nested
  class GivenAuthEnabled {

    @Test
    void whenContextLoads_thenNoStubBeans() {
      // WHEN
      try (var context = contextWith("false")) {
        // THEN
        assertThat(context.getBeansOfType(ClientRegistrationRepository.class)).isEmpty();
        assertThat(context.getBeansOfType(JwtDecoder.class)).isEmpty();
      }
    }
  }
}
