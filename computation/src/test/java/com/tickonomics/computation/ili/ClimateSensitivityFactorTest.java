package com.tickonomics.computation.ili;

import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClimateSensitivityFactorTest {

    private static final double EPSILON = 1e-9;
    private static final String CLIMATE_ENDPOINT = "/api/v1/climate/simulate";

    @Mock
    private AnalyticsWorkerClient client;

    private ClimateSensitivityFactor service;

    @BeforeEach
    void setUp() {
        service = new ClimateSensitivityFactor(client);
    }

    @Nested
    @DisplayName("adjustThresholds - happy path")
    class AdjustThresholdsHappyPath {

        @Test
        @DisplayName("given valid inputs and positive factor when adjustThresholds called then returns adjusted thresholds")
            /** Given a positive climate factor from the remote service and valid rrp/spread/vol.
             *  When adjustThresholds is called.
             *  Then each threshold is multiplied by (1 + factor).
             */
        void givenValidInputsAndPositiveFactor_whenAdjustThresholds_thenReturnsAdjustedThresholds() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 0.2));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertNotNull(result);
            assertEquals(0.2, result.factor(), EPSILON);
            assertEquals(1.0 * 1.2, result.rrpAdjusted(), EPSILON);
            assertEquals(2.0 * 1.2, result.spreadAdjusted(), EPSILON);
            assertEquals(3.0 * 1.2, result.volAdjusted(), EPSILON);
        }

        @Test
        @DisplayName("given valid inputs and zero factor when adjustThresholds called then thresholds unchanged")
            /** Given a zero climate factor from the remote service.
             *  When adjustThresholds is called.
             *  Then each threshold is equal to the original value (multiplied by 1.0).
             */
        void givenValidInputsAndZeroFactor_whenAdjustThresholds_thenThresholdsUnchanged() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 0.0));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.5, 2.5, 3.5);

            assertEquals(0.0, result.factor(), EPSILON);
            assertEquals(1.5, result.rrpAdjusted(), EPSILON);
            assertEquals(2.5, result.spreadAdjusted(), EPSILON);
            assertEquals(3.5, result.volAdjusted(), EPSILON);
        }

        @Test
        @DisplayName("given valid inputs and negative factor when adjustThresholds called then thresholds reduced")
            /** Given a negative climate factor from the remote service.
             *  When adjustThresholds is called.
             *  Then each threshold is reduced (multiplied by less than 1.0).
             */
        void givenValidInputsAndNegativeFactor_whenAdjustThresholds_thenThresholdsReduced() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", -0.3));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(-0.3, result.factor(), EPSILON);
            assertEquals(1.0 * 0.7, result.rrpAdjusted(), EPSILON);
            assertEquals(2.0 * 0.7, result.spreadAdjusted(), EPSILON);
            assertEquals(3.0 * 0.7, result.volAdjusted(), EPSILON);
        }
    }

    @Nested
    @DisplayName("adjustThresholds - caching")
    class AdjustThresholdsCaching {

        @Test
        @DisplayName("given cached factor within TTL when adjustThresholds called twice then client called once")
            /** Given a first call that populates the cache and a second call within the 24h TTL.
             *  When adjustThresholds is called twice.
             *  Then the client is invoked only once and the same factor is returned both times.
             */
        void givenCachedFactorWithinTtl_whenAdjustThresholdsCalledTwice_thenClientCalledOnce() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 0.15));

            ClimateSensitivityFactor.ThresholdAdjustment first =
                    service.adjustThresholds(1.0, 1.0, 1.0);
            ClimateSensitivityFactor.ThresholdAdjustment second =
                    service.adjustThresholds(2.0, 2.0, 2.0);

            assertEquals(0.15, first.factor(), EPSILON);
            assertEquals(0.15, second.factor(), EPSILON);
            verify(client, times(1)).sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap());
        }

        @Test
        @DisplayName("given cache invalidated when adjustThresholds called then client called again")
            /** Given a previously cached factor that is invalidated.
             *  When adjustThresholds is called again.
             *  Then the client is called a second time and the new factor is used.
             */
        void givenCacheInvalidated_whenAdjustThresholds_thenClientCalledAgain() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 0.1))
                    .thenReturn(Map.of("factor", 0.25));

            ClimateSensitivityFactor.ThresholdAdjustment first =
                    service.adjustThresholds(1.0, 1.0, 1.0);
            assertEquals(0.1, first.factor(), EPSILON);

            service.invalidateCache();

            ClimateSensitivityFactor.ThresholdAdjustment second =
                    service.adjustThresholds(1.0, 1.0, 1.0);
            assertEquals(0.25, second.factor(), EPSILON);

            verify(client, times(2)).sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap());
        }
    }

    @Nested
    @DisplayName("adjustThresholds - fallback on failure")
    class AdjustThresholdsFallback {

        @Test
        @DisplayName("given client throws exception when adjustThresholds called then returns fallback factor 0.0")
            /** Given the analytics worker client throws a RuntimeException.
             *  When adjustThresholds is called.
             *  Then the result uses the fallback factor of 0.0, leaving thresholds unchanged.
             */
        void givenClientThrowsException_whenAdjustThresholds_thenReturnsFallbackFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenThrow(new RuntimeException("Connection refused"));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(0.0, result.factor(), EPSILON);
            assertEquals(1.0, result.rrpAdjusted(), EPSILON);
            assertEquals(2.0, result.spreadAdjusted(), EPSILON);
            assertEquals(3.0, result.volAdjusted(), EPSILON);
        }

        @Test
        @DisplayName("given client returns null response when adjustThresholds called then returns fallback factor 0.0")
            /** Given the analytics worker client returns null.
             *  When adjustThresholds is called.
             *  Then the result uses the fallback factor of 0.0.
             */
        void givenClientReturnsNull_whenAdjustThresholds_thenReturnsFallbackFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(null);

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(0.0, result.factor(), EPSILON);
        }

        @Test
        @DisplayName("given client returns error response when adjustThresholds called then returns fallback factor 0.0")
            /** Given the analytics worker client returns a map containing an "error" key.
             *  When adjustThresholds is called.
             *  Then the result uses the fallback factor of 0.0.
             */
        void givenClientReturnsErrorResponse_whenAdjustThresholds_thenReturnsFallbackFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("error", "timeout"));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(0.0, result.factor(), EPSILON);
        }

        @Test
        @DisplayName("given client returns NaN factor when adjustThresholds called then returns fallback factor 0.0")
            /** Given the analytics worker client returns a factor value that is NaN.
             *  When adjustThresholds is called.
             *  Then the result uses the fallback factor of 0.0.
             */
        void givenClientReturnsNaNFactor_whenAdjustThresholds_thenReturnsFallbackFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", Double.NaN));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(0.0, result.factor(), EPSILON);
        }

        @Test
        @DisplayName("given client returns infinite factor when adjustThresholds called then returns fallback factor 0.0")
            /** Given the analytics worker client returns a factor value that is infinite.
             *  When adjustThresholds is called.
             *  Then the result uses the fallback factor of 0.0.
             */
        void givenClientReturnsInfiniteFactor_whenAdjustThresholds_thenReturnsFallbackFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", Double.POSITIVE_INFINITY));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(0.0, result.factor(), EPSILON);
        }

        @Test
        @DisplayName("given client returns response without factor key when adjustThresholds called then returns fallback factor 0.0")
            /** Given the analytics worker client returns a map without a "factor" key.
             *  When adjustThresholds is called.
             *  Then the result uses the fallback factor of 0.0.
             */
        void givenClientReturnsNoFactorKey_whenAdjustThresholds_thenReturnsFallbackFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("status", "ok"));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(0.0, result.factor(), EPSILON);
        }

        @Test
        @DisplayName("given client returns non-numeric factor when adjustThresholds called then returns fallback factor 0.0")
            /** Given the analytics worker client returns a factor that is a String instead of Number.
             *  When adjustThresholds is called.
             *  Then the result uses the fallback factor of 0.0.
             */
        void givenClientReturnsNonNumericFactor_whenAdjustThresholds_thenReturnsFallbackFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", "not-a-number"));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 2.0, 3.0);

            assertEquals(0.0, result.factor(), EPSILON);
        }
    }

    @Nested
    @DisplayName("adjustThresholds - edge case inputs")
    class AdjustThresholdsEdgeCases {

        @Test
        @DisplayName("given zero inputs when adjustThresholds called then returns zero adjusted values")
            /** Given zero values for rrp, spread, and vol, and a positive climate factor.
             *  When adjustThresholds is called.
             *  Then all adjusted values remain zero.
             */
        void givenZeroInputs_whenAdjustThresholds_thenReturnsZeroAdjustedValues() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 0.5));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(0.0, 0.0, 0.0);

            assertEquals(0.5, result.factor(), EPSILON);
            assertEquals(0.0, result.rrpAdjusted(), EPSILON);
            assertEquals(0.0, result.spreadAdjusted(), EPSILON);
            assertEquals(0.0, result.volAdjusted(), EPSILON);
        }

        @Test
        @DisplayName("given negative inputs when adjustThresholds called then adjustment applied correctly")
            /** Given negative rrp, spread, and vol values with a positive climate factor.
             *  When adjustThresholds is called.
             *  Then the multiplicative adjustment is applied to each negative value.
             */
        void givenNegativeInputs_whenAdjustThresholds_thenAdjustmentAppliedCorrectly() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 0.1));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(-1.0, -2.0, -3.0);

            assertEquals(-1.0 * 1.1, result.rrpAdjusted(), EPSILON);
            assertEquals(-2.0 * 1.1, result.spreadAdjusted(), EPSILON);
            assertEquals(-3.0 * 1.1, result.volAdjusted(), EPSILON);
        }

        @Test
        @DisplayName("given large factor when adjustThresholds called then thresholds amplified accordingly")
            /** Given a large climate factor of 5.0 from the remote service.
             *  When adjustThresholds is called with positive inputs.
             *  Then each threshold is multiplied by 6.0 (1 + 5.0).
             */
        void givenLargeFactor_whenAdjustThresholds_thenThresholdsAmplified() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 5.0));

            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 1.0, 1.0);

            assertEquals(6.0, result.rrpAdjusted(), EPSILON);
            assertEquals(6.0, result.spreadAdjusted(), EPSILON);
            assertEquals(6.0, result.volAdjusted(), EPSILON);
        }
    }

    @Nested
    @DisplayName("ThresholdAdjustment record")
    class ThresholdAdjustmentRecord {

        @Test
        @DisplayName("given record fields when accessed then values match constructor args")
            /** Given a ThresholdAdjustment constructed with known values.
             *  When accessor methods are called.
             *  Then the values match those passed to the constructor.
             */
        void givenRecordFields_whenAccessed_thenValuesMatchConstructorArgs() {
            var adjustment = new ClimateSensitivityFactor.ThresholdAdjustment(
                    0.3, 1.3, 2.6, 3.9);

            assertEquals(0.3, adjustment.factor(), EPSILON);
            assertEquals(1.3, adjustment.rrpAdjusted(), EPSILON);
            assertEquals(2.6, adjustment.spreadAdjusted(), EPSILON);
            assertEquals(3.9, adjustment.volAdjusted(), EPSILON);
        }
    }

    @Nested
    @DisplayName("invalidateCache")
    class InvalidateCache {

        @Test
        @DisplayName("given cached factor when invalidateCache called then next call fetches fresh factor")
            /** Given a previously cached factor of 0.1.
             *  When invalidateCache is called and adjustThresholds is invoked again.
             *  Then the client is called twice total and the new factor replaces the old one.
             */
        void givenCachedFactor_whenInvalidateCache_thenNextCallFetchesFreshFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("factor", 0.1))
                    .thenReturn(Map.of("factor", 0.4));

            service.adjustThresholds(1.0, 1.0, 1.0);
            service.invalidateCache();
            ClimateSensitivityFactor.ThresholdAdjustment result =
                    service.adjustThresholds(1.0, 1.0, 1.0);

            assertEquals(0.4, result.factor(), EPSILON);
            verify(client, times(2)).sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap());
        }
    }

    @Nested
    @DisplayName("adjustThresholds - fallback caches result")
    class FallbackCaching {

        @Test
        @DisplayName("given client fails then succeeds when adjustThresholds called twice then second call fetches fresh factor")
            /** Given a first call that fails (returns error) and a second call after cache invalidation that succeeds.
             *  When adjustThresholds is called twice with invalidation between.
             *  Then the first call returns fallback 0.0 and the second returns the actual factor.
             */
        void givenClientFailsThenSucceeds_whenAdjustThresholds_thenSecondCallReturnsFreshFactor() {
            when(client.sendAnalysisRequest(eq(CLIMATE_ENDPOINT), anyMap()))
                    .thenReturn(Map.of("error", "unavailable"))
                    .thenReturn(Map.of("factor", 0.33));

            ClimateSensitivityFactor.ThresholdAdjustment first =
                    service.adjustThresholds(1.0, 1.0, 1.0);
            assertEquals(0.0, first.factor(), EPSILON);

            service.invalidateCache();

            ClimateSensitivityFactor.ThresholdAdjustment second =
                    service.adjustThresholds(1.0, 1.0, 1.0);
            assertEquals(0.33, second.factor(), EPSILON);
        }
    }
}
