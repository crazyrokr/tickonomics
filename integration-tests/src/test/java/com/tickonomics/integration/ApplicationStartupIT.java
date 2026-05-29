package com.tickonomics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApplicationStartupIT extends AbstractIntegrationTest {

  @Autowired
  private TestRestTemplate restTemplate;

  @Nested
  class HealthEndpoint {
    @Test
    void givenRunningApplication_whenHealthEndpoint_thenReturnsUp() {
      ResponseEntity<Map> response = restTemplate.getForEntity("/health", Map.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).containsEntry("status", "UP");
    }
  }

  @Nested
  class ApplicationContextLoad {
    @Test
    void givenTestConfiguration_whenContextLoads_thenNoExceptions() {
      assertThat(restTemplate).isNotNull();
    }
  }
}
