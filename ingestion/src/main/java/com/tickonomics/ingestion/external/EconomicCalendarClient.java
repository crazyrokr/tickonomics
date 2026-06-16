package com.tickonomics.ingestion.external;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(name = "monitor.ingestion.economic-calendar.enabled", havingValue = "true")
public class EconomicCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(EconomicCalendarClient.class);

    private final RestClient restClient;

    @Value("${monitor.ingestion.economic-calendar.base-url:https://api.stlouisfed.org/fred}")
    private String baseUrl;

    @Value("${monitor.ingestion.economic-calendar.api-key:}")
    private String apiKey;

    public EconomicCalendarClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Scheduled(fixedDelayString = "${monitor.ingestion.economic-calendar.poll-interval-ms:3600000}")
    @Retryable(includes = RestClientException.class, maxRetries = 2, delay = 1000, multiplier = 2)
    public void pollUpcomingEvents() {
        try {
            var events = fetchCalendarEvents();
            log.info("Fetched {} economic calendar events", events.size());
        } catch (Exception e) {
            log.error("Failed to fetch economic calendar: {}", e.getMessage());
        }
    }

    public List<EconomicEvent> fetchCalendarEvents() {
        String url = baseUrl + "/releases?api_key={apiKey}&file_type=json&limit=20";
        var response = restClient.get()
                .uri(url, apiKey)
                .retrieve()
                .body(FredReleasesResponse.class);

        if (response == null || response.releases() == null) {
            return List.of();
        }

        return response.releases().stream()
                .map(this::toEvent)
                .toList();
    }

    private EconomicEvent toEvent(FredRelease r) {
        return new EconomicEvent(
                r.id(),
                r.name(),
                LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                "ECONOMIC_EVENT",
                "FRED");
    }

    public record EconomicEvent(long releaseId, String name, Instant time, String eventType, String source) {}

    record FredReleasesResponse(List<FredRelease> releases) {}

    record FredRelease(long id, String name) {}
}
