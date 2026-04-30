package com.tickonomics.ingestion.nyfed;

import com.tickonomics.cdm.adapter.NyFedCdmAdapter;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NyFedClientTest {

    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RateSnapshotRepository rateRepository;
    @Mock private RestClient restClient;

    private NyFedClient client;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        client = new NyFedClient(restClientBuilder, rateRepository, new NyFedCdmAdapter());
    }

    @Nested
    class FetchRates {
        @Test
        void givenValidRates_whenFetchRates_thenReturnMappedResponses() {
            var rates = List.of(
                    new NyFedClient.NyFedRateRaw("2026-05-23", 4.29),
                    new NyFedClient.NyFedRateRaw("2026-05-22", 4.28));
            var response = new NyFedClient.NyFedRatesApiResponse(rates);

            var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            var responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(NyFedClient.NyFedRatesApiResponse.class)).thenReturn(response);

            var results = client.fetchRates("sofr");
            assertEquals(2, results.size());
            assertEquals(4.29, results.getFirst().value());
            assertEquals("sofr", results.getFirst().rateType());
        }

        @Test
        void givenNullRate_whenFetchRates_thenFiltered() {
            var rates = List.of(
                    new NyFedClient.NyFedRateRaw("2026-05-23", null),
                    new NyFedClient.NyFedRateRaw("2026-05-22", 4.28));
            var response = new NyFedClient.NyFedRatesApiResponse(rates);

            var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            var responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(NyFedClient.NyFedRatesApiResponse.class)).thenReturn(response);

            var results = client.fetchRates("sofr");
            assertEquals(1, results.size());
        }

        @Test
        void givenNullResponse_whenFetchRates_thenReturnEmpty() {
            var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            var responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(NyFedClient.NyFedRatesApiResponse.class)).thenReturn(null);

            var results = client.fetchRates("sofr");
            assertTrue(results.isEmpty());
        }
    }
}
