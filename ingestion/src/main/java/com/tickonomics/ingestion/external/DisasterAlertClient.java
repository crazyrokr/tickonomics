package com.tickonomics.ingestion.external;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class DisasterAlertClient {

    private static final Logger log = LoggerFactory.getLogger(DisasterAlertClient.class);
    private static final String USGS_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query?minmagnitude=5.0&format=geojson&limit=10";
    private static final int MAX_QUEUE_SIZE = 100;
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration CIRCUIT_OPEN_DURATION = Duration.ofSeconds(60);
    private static final Duration CRITICAL_WINDOW = Duration.ofHours(1);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final Queue<DisasterAlert> alertQueue = new ConcurrentLinkedQueue<>();
    private int consecutiveFailures = 0;
    private Instant circuitOpenUntil = Instant.MIN;

    public DisasterAlertClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 30_000)
    void pollEarthquakeFeed() {
        if (isCircuitBreakerOpen()) {
            log.debug("Circuit breaker open, skipping poll");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USGS_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                handleFailure("HTTP " + response.statusCode());
                return;
            }

            parseAndStoreAlerts(response.body());
            consecutiveFailures = 0;
        } catch (Exception e) {
            handleFailure(e.getMessage());
        }
    }

    boolean hasCriticalAlerts() {
        Instant cutoff = Instant.now().minus(CRITICAL_WINDOW);
        return alertQueue.stream()
                .anyMatch(alert -> alert.severity() == Severity.CRITICAL
                        && alert.timestamp().isAfter(cutoff));
    }

    void triggerExogenousShock(DisasterAlert alert) {
        log.info("EXOGENOUS_SHOCK source={} eventId={} magnitude={} location={} severity={}",
                alert.source(), alert.eventId(), alert.magnitude(),
                alert.location(), alert.severity());
    }

    Queue<DisasterAlert> getAlertQueue() {
        return alertQueue;
    }

    boolean isCircuitBreakerOpen() {
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            if (Instant.now().isBefore(circuitOpenUntil)) {
                return true;
            }
            consecutiveFailures = 0;
        }
        return false;
    }

    int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    void parseAndStoreAlerts(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode features = root.path("features");

            if (!features.isArray()) {
                return;
            }

            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties");
                double magnitude = properties.path("mag").asDouble(0.0);
                String place = properties.path("place").asText("unknown");
                String eventId = feature.path("id").asText("unknown");
                long timeMillis = properties.path("time").asLong(0L);

                Severity severity = classifySeverity(magnitude);
                DisasterAlert alert = new DisasterAlert(
                        "USGS", eventId, magnitude, place,
                        Instant.ofEpochMilli(timeMillis), severity);

                enqueueAlert(alert);
                triggerExogenousShock(alert);
            }
        } catch (Exception e) {
            handleFailure("Parse error: " + e.getMessage());
        }
    }

    void handleFailure(String reason) {
        consecutiveFailures++;
        log.warn("Disaster feed poll failed ({}/{}): {}", consecutiveFailures, FAILURE_THRESHOLD, reason);

        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            circuitOpenUntil = Instant.now().plus(CIRCUIT_OPEN_DURATION);
            log.error("Circuit breaker OPEN for {}s after {} consecutive failures", CIRCUIT_OPEN_DURATION.getSeconds(), FAILURE_THRESHOLD);
        }
    }

    void enqueueAlert(DisasterAlert alert) {
        while (alertQueue.size() >= MAX_QUEUE_SIZE) {
            alertQueue.poll();
        }
        alertQueue.offer(alert);
    }

    static Severity classifySeverity(double magnitude) {
        if (magnitude >= 7.0) {
            return Severity.CRITICAL;
        } else if (magnitude >= 5.0) {
            return Severity.HIGH;
        }
        return Severity.MODERATE;
    }

    public enum Severity {
        CRITICAL, HIGH, MODERATE
    }

    public record DisasterAlert(
            String source,
            String eventId,
            double magnitude,
            String location,
            Instant timestamp,
            Severity severity
    ) {
    }
}
