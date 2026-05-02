package com.tickonomics.computation.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestClientAnalyticsWorkerClientTest {

    private static final String UNREACHABLE_URL = "http://localhost:59999";

    private RestClientAnalyticsWorkerClient client;

    @BeforeEach
    void setUp() {
        client = new RestClientAnalyticsWorkerClient(
                RestClient.builder(), UNREACHABLE_URL);
    }

    @Nested
    class SendAnalysisRequest {
        @Test
        void givenUnreachableServer_whenSend_thenErrorMap() {
            var result = client.sendAnalysisRequest("/api/v1/econometrics/adf",
                    Map.of("series", new double[0]));
            assertTrue(result.containsKey("error"));
        }
    }

    @Nested
    class IsHealthy {
        @Test
        void givenUnreachableServer_whenHealthy_thenFalse() {
            assertFalse(client.isHealthy());
        }
    }

    @Nested
    class SendArrowRequest {
        @Test
        void givenUnreachableServer_whenArrow_thenEmptyArray() {
            var result = client.sendArrowRequest(new byte[]{1, 2, 3});
            assertEquals(0, result.length);
        }
    }
}
