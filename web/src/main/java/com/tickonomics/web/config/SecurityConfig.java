package com.tickonomics.web.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final SecurityProperties securityProperties;
  private final List<String> allowedOrigins;

  public SecurityConfig(
      SecurityProperties securityProperties,
      @Value("${security.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
      List<String> allowedOrigins) {
    this.securityProperties = securityProperties;
    this.allowedOrigins = allowedOrigins;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    if (securityProperties.authDisabled()) {
      return http
          .securityMatcher("/**")
          .cors(cors -> cors.configurationSource(corsConfigurationSource()))
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .headers(headers -> headers
              .contentSecurityPolicy(csp -> csp.policyDirectives(
                  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"))
              .frameOptions(fo -> fo.deny())
              .httpStrictTransportSecurity(hsts -> hsts
                  .includeSubDomains(true).maxAgeInSeconds(31536000)))
          .build();
    }

    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/health", "/actuator/**").permitAll()
            .requestMatchers("/oauth2/**", "/login/oauth2/code/**").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> {}))
        .oauth2Client(oauth2 -> {})
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"))
            .frameOptions(fo -> fo.deny())
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true).maxAgeInSeconds(31536000)))
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
