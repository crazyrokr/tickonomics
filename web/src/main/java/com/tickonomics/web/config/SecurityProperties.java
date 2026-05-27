package com.tickonomics.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
    boolean authDisabled,
    String jwtIssuerUri,
    String jwtJwkSetUri,
    String oauth2ClientId,
    String oauth2ClientSecret,
    String oauth2ProviderUri) {

  public SecurityProperties {
    if (jwtIssuerUri == null) {
      jwtIssuerUri = "";
    }
    if (jwtJwkSetUri == null) {
      jwtJwkSetUri = "";
    }
    if (oauth2ClientId == null) {
      oauth2ClientId = "tickonomics-dashboard";
    }
    if (oauth2ClientSecret == null) {
      oauth2ClientSecret = "";
    }
    if (oauth2ProviderUri == null) {
      oauth2ProviderUri = "";
    }
  }
}
