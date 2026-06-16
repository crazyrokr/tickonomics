package com.tickonomics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class ApplicationStartupIT extends AbstractIntegrationTest {

  @LocalServerPort
  private int port;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Nested
  class HealthEndpoint {
    @Test
    void givenRunningApplication_whenHealthEndpoint_thenReturnsUp() {
      ResponseEntity<Map> response = restClient.get()
          .uri("/health")
          .retrieve()
          .toEntity(Map.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).containsEntry("status", "UP");
    }
  }

  @Nested
  class ApplicationContextLoad {
    @Test
    void givenTestConfiguration_whenContextLoads_thenNoExceptions() {
      assertThat(restClient).isNotNull();
    }
  }
}
