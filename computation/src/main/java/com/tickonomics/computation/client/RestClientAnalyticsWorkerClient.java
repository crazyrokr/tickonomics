package com.tickonomics.computation.client;

import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class RestClientAnalyticsWorkerClient implements AnalyticsWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientAnalyticsWorkerClient.class);

    private final RestClient restClient;

    public RestClientAnalyticsWorkerClient(
            RestClient.Builder restClientBuilder,
            @Value("${analytics.worker.url:http://localhost:8001}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    @Retry(name = "analyticsWorker")
    @SuppressWarnings("unchecked")
    public Map<String, Object> sendAnalysisRequest(String function, Map<String, Object> payload) {
        try {
            return restClient.post()
                    .uri(function)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("Analytics worker request failed for {}: {}", function, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @Override
    public byte[] sendArrowRequest(byte[] arrowPayload) {
        try {
            return restClient.post()
                    .uri("/api/v1/arrow/analyze")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(arrowPayload)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("Arrow request failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            var response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toEntity(String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
