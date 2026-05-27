package com.tickonomics.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigTest {

  @Nested
  class SecurityPropertiesConstruction {
    @Test
    void givenDefaults_whenConstruct_thenFieldsAreDefaults() {
      var props = new SecurityProperties(false, null, null, null, null, null);
      assertFalse(props.authDisabled());
      assertEquals("", props.jwtIssuerUri());
      assertEquals("", props.jwtJwkSetUri());
      assertEquals("tickonomics-dashboard", props.oauth2ClientId());
      assertEquals("", props.oauth2ClientSecret());
      assertEquals("", props.oauth2ProviderUri());
    }

    @Test
    void givenCustomValues_whenConstruct_thenFieldsAreCustom() {
      var props = new SecurityProperties(
          true,
          "https://auth.example.com",
          "https://auth.example.com/jwks",
          "my-client",
          "my-secret",
          "https://auth.example.com");
      assertTrue(props.authDisabled());
      assertEquals("https://auth.example.com", props.jwtIssuerUri());
      assertEquals("https://auth.example.com/jwks", props.jwtJwkSetUri());
      assertEquals("my-client", props.oauth2ClientId());
      assertEquals("my-secret", props.oauth2ClientSecret());
      assertEquals("https://auth.example.com", props.oauth2ProviderUri());
    }

    @Test
    void givenEmptyStrings_whenConstruct_thenEmptyStringsPreserved() {
      var props = new SecurityProperties(false, "", "", "", "", "");
      assertEquals("", props.jwtIssuerUri());
      assertEquals("", props.jwtJwkSetUri());
      assertEquals("", props.oauth2ClientId());
    }
  }

  @Nested
  class CorsConfigurationSource {
    @Test
    void givenAllowedOrigins_whenCorsConfigured_thenCorsSourceCreated() throws Exception {
      var config = new SecurityConfig();
      Field originsField = SecurityConfig.class.getDeclaredField("allowedOrigins");
      originsField.setAccessible(true);
      originsField.set(config, List.of("http://localhost:3000", "http://localhost:3001"));

      var source = config.corsConfigurationSource();
      assertNotNull(source);
      assertTrue(source instanceof UrlBasedCorsConfigurationSource);

      var urlSource = (UrlBasedCorsConfigurationSource) source;
      var corsConfig = urlSource.getCorsConfigurations().get("/**");
      assertNotNull(corsConfig);
      assertTrue(corsConfig.getAllowedOrigins().contains("http://localhost:3000"));
      assertTrue(corsConfig.getAllowedMethods().contains("GET"));
      assertTrue(corsConfig.getAllowedHeaders().contains("Authorization"));
      assertTrue(corsConfig.getAllowCredentials());
    }
  }
}
