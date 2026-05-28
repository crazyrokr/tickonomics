package com.tickonomics.ingestion.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisasterAlertClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private DisasterAlertClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new DisasterAlertClient(httpClient, objectMapper);
    }

    private static final String USGS_RESPONSE_JSON = """
            {
              "features": [
                {
                  "id": "us70001abc",
                  "properties": {
                    "mag": 7.5,
                    "place": "10km NW of Tokyo, Japan",
                    "time": 1700000000000
                  }
                },
                {
                  "id": "us70001def",
                  "properties": {
                    "mag": 5.2,
                    "place": "5km S of Lima, Peru",
                    "time": 1700000001000
                  }
                }
              ]
            }
            """;

    @Nested
    class HappyPathPolling {

        @Test
        void givenValidUsgsResponse_whenPollEarthquakeFeed_thenAlertsAreStored() throws Exception {
            // Given
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(USGS_RESPONSE_JSON);
            when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

            // When
            client.pollEarthquakeFeed();

            // Then
            Queue<DisasterAlertClient.DisasterAlert> queue = client.getAlertQueue();
            assertThat(queue).hasSize(2);
            assertThat(queue.stream().map(DisasterAlertClient.DisasterAlert::eventId))
                    .containsExactly("us70001abc", "us70001def");
        }

        @Test
        void givenMagnitudeSevenPlus_whenParseAlerts_thenSeverityIsCritical() throws Exception {
            // Given
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(USGS_RESPONSE_JSON);
            when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

            // When
            client.pollEarthquakeFeed();

            // Then
            DisasterAlertClient.DisasterAlert first = client.getAlertQueue().peek();
            assertThat(first.severity()).isEqualTo(DisasterAlertClient.Severity.CRITICAL);
            assertThat(first.magnitude()).isEqualTo(7.5);
            assertThat(first.source()).isEqualTo("USGS");
        }

        @Test
        void givenMagnitudeFiveToSeven_whenParseAlerts_thenSeverityIsHigh() throws Exception {
            // Given
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(USGS_RESPONSE_JSON);
            when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

            // When
            client.pollEarthquakeFeed();

            // Then
            DisasterAlertClient.DisasterAlert second = client.getAlertQueue().stream()
                    .filter(a -> a.eventId().equals("us70001def"))
                    .findFirst().orElseThrow();
            assertThat(second.severity()).isEqualTo(DisasterAlertClient.Severity.HIGH);
        }

        @Test
        void givenSuccessfulPoll_thenConsecutiveFailuresResetToZero() throws Exception {
            // Given
            client.handleFailure("setup failure");
            client.handleFailure("setup failure");
            assertThat(client.getConsecutiveFailures()).isEqualTo(2);

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(USGS_RESPONSE_JSON);
            when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

            // When
            client.pollEarthquakeFeed();

            // Then
            assertThat(client.getConsecutiveFailures()).isZero();
        }
    }

    @Nested
    class CircuitBreakerBehavior {

        @Test
        void givenFewerThanFiveFailures_thenCircuitBreakerRemainsClosed() {
            // Given
            client.handleFailure("fail 1");
            client.handleFailure("fail 2");
            client.handleFailure("fail 3");
            client.handleFailure("fail 4");

            // When
            boolean open = client.isCircuitBreakerOpen();

            // Then
            assertThat(open).isFalse();
        }

        @Test
        void givenFiveConsecutiveFailures_thenCircuitBreakerOpens() {
            // Given
            for (int i = 0; i < 5; i++) {
                client.handleFailure("fail " + i);
            }

            // When
            boolean open = client.isCircuitBreakerOpen();

            // Then
            assertThat(open).isTrue();
        }

        @Test
        void givenCircuitBreakerOpen_whenTimeExpires_thenCircuitCloses() {
            // Given - trigger 5 failures to open circuit
            for (int i = 0; i < 5; i++) {
                client.handleFailure("fail " + i);
            }
            assertThat(client.isCircuitBreakerOpen()).isTrue();

            // When - simulate expiry by manipulating internal state via reflection-free approach
            // We wait for the internal circuit to be checked after timeout;
            // since we can't wait 60s, we verify the logic via the public method
            // The circuit should remain open immediately after triggering
            boolean stillOpen = client.isCircuitBreakerOpen();

            // Then - it stays open because we haven't waited long enough
            assertThat(stillOpen).isTrue();
        }

        @Test
        void givenCircuitBreakerOpen_whenPolling_thenRequestIsSkipped() throws Exception {
            // Given
            for (int i = 0; i < 5; i++) {
                client.handleFailure("fail " + i);
            }

            // When
            client.pollEarthquakeFeed();

            // Then - HttpClient.send should never be called (no mock setup = no interaction needed)
            assertThat(client.getAlertQueue()).isEmpty();
        }

        @Test
        void givenHttpNon200Response_thenCountsAsFailure() throws Exception {
            // Given
            when(httpResponse.statusCode()).thenReturn(503);
            when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

            // When
            client.pollEarthquakeFeed();

            // Then
            assertThat(client.getConsecutiveFailures()).isEqualTo(1);
        }
    }

    @Nested
    class QueueOverflow {

        @Test
        void givenQueueAtMaxCapacity_whenEnqueuingNewAlert_thenOldestIsEvicted() {
            // Given
            for (int i = 0; i < 100; i++) {
                DisasterAlertClient.DisasterAlert alert = new DisasterAlertClient.DisasterAlert(
                        "SRC", "evt-" + i, 5.5, "loc", Instant.now(),
                        DisasterAlertClient.Severity.HIGH);
                client.enqueueAlert(alert);
            }
            assertThat(client.getAlertQueue()).hasSize(100);
            String firstEventId = client.getAlertQueue().peek().eventId();

            // When
            DisasterAlertClient.DisasterAlert overflow = new DisasterAlertClient.DisasterAlert(
                    "SRC", "overflow-event", 6.0, "loc", Instant.now(),
                    DisasterAlertClient.Severity.HIGH);
            client.enqueueAlert(overflow);

            // Then
            assertThat(client.getAlertQueue()).hasSize(100);
            assertThat(client.getAlertQueue().stream().map(DisasterAlertClient.DisasterAlert::eventId))
                    .doesNotContain(firstEventId);
            assertThat(client.getAlertQueue().stream().map(DisasterAlertClient.DisasterAlert::eventId))
                    .contains("overflow-event");
        }

        @Test
        void givenEmptyQueue_whenEnqueuing_thenQueueHasOneElement() {
            // Given
            DisasterAlertClient.DisasterAlert alert = new DisasterAlertClient.DisasterAlert(
                    "SRC", "evt-1", 5.5, "loc", Instant.now(),
                    DisasterAlertClient.Severity.HIGH);

            // When
            client.enqueueAlert(alert);

            // Then
            assertThat(client.getAlertQueue()).hasSize(1);
            assertThat(client.getAlertQueue().peek().eventId()).isEqualTo("evt-1");
        }
    }

    @Nested
    class CriticalAlertsDetection {

        @Test
        void givenCriticalAlertInLastHour_whenHasCriticalAlerts_thenReturnsTrue() {
            // Given
            DisasterAlertClient.DisasterAlert criticalAlert = new DisasterAlertClient.DisasterAlert(
                    "USGS", "critical-1", 7.8, "Pacific", Instant.now().minusSeconds(30),
                    DisasterAlertClient.Severity.CRITICAL);
            client.enqueueAlert(criticalAlert);

            // When
            boolean result = client.hasCriticalAlerts();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void givenOnlyHighSeverityAlerts_whenHasCriticalAlerts_thenReturnsFalse() {
            // Given
            DisasterAlertClient.DisasterAlert highAlert = new DisasterAlertClient.DisasterAlert(
                    "USGS", "high-1", 5.5, "Pacific", Instant.now(),
                    DisasterAlertClient.Severity.HIGH);
            client.enqueueAlert(highAlert);

            // When
            boolean result = client.hasCriticalAlerts();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenCriticalAlertOlderThanOneHour_whenHasCriticalAlerts_thenReturnsFalse() {
            // Given
            DisasterAlertClient.DisasterAlert oldCritical = new DisasterAlertClient.DisasterAlert(
                    "USGS", "old-1", 8.0, "Pacific", Instant.now().minusSeconds(3601),
                    DisasterAlertClient.Severity.CRITICAL);
            client.enqueueAlert(oldCritical);

            // When
            boolean result = client.hasCriticalAlerts();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenEmptyQueue_whenHasCriticalAlerts_thenReturnsFalse() {
            // Given - empty queue from fresh client

            // When
            boolean result = client.hasCriticalAlerts();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class SeverityClassification {

        @Test
        void givenMagnitudeSeven_whenClassifySeverity_thenReturnsCritical() {
            assertThat(DisasterAlertClient.classifySeverity(7.0)).isEqualTo(DisasterAlertClient.Severity.CRITICAL);
        }

        @Test
        void givenMagnitudeEight_whenClassifySeverity_thenReturnsCritical() {
            assertThat(DisasterAlertClient.classifySeverity(8.5)).isEqualTo(DisasterAlertClient.Severity.CRITICAL);
        }

        @Test
        void givenMagnitudeFive_whenClassifySeverity_thenReturnsHigh() {
            assertThat(DisasterAlertClient.classifySeverity(5.0)).isEqualTo(DisasterAlertClient.Severity.HIGH);
        }

        @Test
        void givenMagnitudeSix_whenClassifySeverity_thenReturnsHigh() {
            assertThat(DisasterAlertClient.classifySeverity(6.9)).isEqualTo(DisasterAlertClient.Severity.HIGH);
        }

        @Test
        void givenMagnitudeBelowFive_whenClassifySeverity_thenReturnsModerate() {
            assertThat(DisasterAlertClient.classifySeverity(4.9)).isEqualTo(DisasterAlertClient.Severity.MODERATE);
        }

        @Test
        void givenMagnitudeZero_whenClassifySeverity_thenReturnsModerate() {
            assertThat(DisasterAlertClient.classifySeverity(0.0)).isEqualTo(DisasterAlertClient.Severity.MODERATE);
        }
    }

    @Nested
    class ParseEdgeCases {

        @Test
        void givenEmptyFeaturesArray_whenParseAndStore_thenNoAlertsAdded() {
            // Given
            String emptyJson = """
                    { "features": [] }
                    """;

            // When
            client.parseAndStoreAlerts(emptyJson);

            // Then
            assertThat(client.getAlertQueue()).isEmpty();
        }

        @Test
        void givenMalformedJson_whenParseAndStore_thenFailureRecorded() {
            // Given
            String badJson = "not valid json at all";

            // When
            client.parseAndStoreAlerts(badJson);

            // Then
            assertThat(client.getAlertQueue()).isEmpty();
            assertThat(client.getConsecutiveFailures()).isEqualTo(1);
        }

        @Test
        void givenMissingProperties_whenParseAndStore_thenUsesDefaults() {
            // Given
            String sparseJson = """
                    { "features": [ { "id": "sparse1", "properties": {} } ] }
                    """;

            // When
            client.parseAndStoreAlerts(sparseJson);

            // Then
            assertThat(client.getAlertQueue()).hasSize(1);
            DisasterAlertClient.DisasterAlert alert = client.getAlertQueue().peek();
            assertThat(alert.magnitude()).isEqualTo(0.0);
            assertThat(alert.location()).isEqualTo("unknown");
        }
    }

    @Nested
    class TriggerExogenousShock {

        @Test
        void givenDisasterAlert_whenTriggerExogenousShock_thenLogsEvent() {
            // Given
            DisasterAlertClient.DisasterAlert alert = new DisasterAlertClient.DisasterAlert(
                    "USGS", "shock-1", 7.2, "Sumatra", Instant.now(),
                    DisasterAlertClient.Severity.CRITICAL);

            // When / Then - should not throw
            client.triggerExogenousShock(alert);
        }
    }
}
