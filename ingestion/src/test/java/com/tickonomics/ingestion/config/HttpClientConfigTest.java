package com.tickonomics.ingestion.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HttpClientConfig")
class HttpClientConfigTest {

    private final HttpClientConfig config = new HttpClientConfig();

    @Test
    @DisplayName("Should provide an HttpClient with connect timeout configured")
    void shouldProvideHttpClientWithConnectTimeout() {
        // When
        HttpClient client = config.httpClient();

        // Then
        assertThat(client).isNotNull();
        assertThat(client.connectTimeout())
            .describedAs("Connect timeout must be set to prevent indefinite hangs")
            .isPresent()
            .hasValue(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Should return a new HttpClient instance on each call")
    void shouldReturnNewInstanceOnEachCall() {
        // When
        HttpClient client1 = config.httpClient();
        HttpClient client2 = config.httpClient();

        // Then
        assertThat(client1).isNotSameAs(client2);
    }
}
