package com.tickonomics.ingestion.fred;

import com.tickonomics.cdm.adapter.FredCdmAdapter;
import com.tickonomics.cdm.adapter.raw.FredObservation;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FredClientTest {

    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RateSnapshotRepository rateRepository;
    @Mock private RestClient restClient;

    private FredClient client;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        client = new FredClient(restClientBuilder, rateRepository, new FredCdmAdapter());
    }

    @Nested
    class FetchSeries {
        @Test
        void givenValidObservations_whenFetchSeries_thenReturnMappedObservations() {
            var obs = List.of(
                    new FredClient.FredObservationRaw("2026-05-23", "4.33"),
                    new FredClient.FredObservationRaw("2026-05-22", "4.35"));
            var response = new FredClient.FredSeriesResponse(obs);

            var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            var responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(String.class), any(Object[].class))).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(FredClient.FredSeriesResponse.class)).thenReturn(response);

            var results = client.fetchSeries("EFFR");
            assertEquals(2, results.size());
            assertEquals(4.33, results.get(0).value());
            assertEquals("EFFR", results.get(0).seriesId());
        }

        @Test
        void givenDotValue_whenFetchSeries_thenFiltered() {
            var obs = List.of(
                    new FredClient.FredObservationRaw("2026-05-23", "."),
                    new FredClient.FredObservationRaw("2026-05-22", "4.35"));
            var response = new FredClient.FredSeriesResponse(obs);

            var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            var responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(String.class), any(Object[].class))).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(FredClient.FredSeriesResponse.class)).thenReturn(response);

            var results = client.fetchSeries("EFFR");
            assertEquals(1, results.size());
            assertEquals(4.35, results.get(0).value());
        }

        @Test
        void givenNullResponse_whenFetchSeries_thenReturnEmpty() {
            var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            var responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(String.class), any(Object[].class))).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(FredClient.FredSeriesResponse.class)).thenReturn(null);

            var results = client.fetchSeries("EFFR");
            assertTrue(results.isEmpty());
        }
    }
}
