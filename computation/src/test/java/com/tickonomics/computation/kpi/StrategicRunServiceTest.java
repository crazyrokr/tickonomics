package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class StrategicRunServiceTest {

    @InjectMocks
    private StrategicRunService service;

    private StrategicRunService.TradeRecord trade(long epochSecond, String side, double qty, double price) {
        return new StrategicRunService.TradeRecord(Instant.ofEpochSecond(epochSecond), side, qty, price);
    }

    @Nested
    class AnalyzeRuns {
        @Test
        void givenEmptyList_whenAnalyze_thenEmptyRunsAndZeroTransitions() {
            /* Given an empty trade list */
            /* When analyzing runs */
            /* Then result has zero runs and zero transitions */
            var result = service.analyzeRuns(List.of());

            assertTrue(result.runs().isEmpty());
            assertEquals(0, result.transitions().passiveToAggressive());
            assertEquals(0, result.transitions().aggressiveToPassive());
            assertEquals(0, result.transitions().sameSide());
        }

        @Test
        void givenNullList_whenAnalyze_thenEmptyRunsAndZeroTransitions() {
            /* Given null trade list */
            /* When analyzing runs */
            /* Then result has zero runs and zero transitions */
            var result = service.analyzeRuns(null);

            assertTrue(result.runs().isEmpty());
            assertEquals(0, result.transitions().passiveToAggressive());
        }

        @Test
        void givenSingleTrade_whenAnalyze_thenOneRun() {
            /* Given a single trade */
            /* When analyzing runs */
            /* Then one run is produced */
            var trades = List.of(trade(100, "BUY", 100, 50.0));
            var result = service.analyzeRuns(trades);

            assertEquals(1, result.runs().size());
            assertEquals("BUY", result.runs().get(0).direction());
            assertEquals(100, result.runs().get(0).totalVolume());
        }

        @Test
        void givenSameSideTrades_whenAnalyze_thenSingleRun() {
            /* Given consecutive BUY trades within gap threshold */
            /* When analyzing runs */
            /* Then they form a single run */
            var trades = List.of(
                    trade(100, "BUY", 100, 50.0),
                    trade(101, "BUY", 200, 50.1),
                    trade(103, "BUY", 150, 50.2)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(1, result.runs().size());
            assertEquals(3, result.runs().get(0).childOrderCount());
            assertEquals(450, result.runs().get(0).totalVolume());
        }

        @Test
        void givenSideChange_whenAnalyze_thenNewRun() {
            /* Given trades that change side */
            /* When analyzing runs */
            /* Then two separate runs are created */
            var trades = List.of(
                    trade(100, "BUY", 100, 50.0),
                    trade(101, "BUY", 100, 50.1),
                    trade(102, "SELL", 200, 49.9),
                    trade(103, "SELL", 100, 49.8)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(2, result.runs().size());
            assertEquals("BUY", result.runs().get(0).direction());
            assertEquals("SELL", result.runs().get(1).direction());
            assertEquals(1, result.transitions().passiveToAggressive());
        }

        @Test
        void givenGapExceeds5s_whenAnalyze_thenNewRun() {
            /* Given trades with a gap > 5 seconds */
            /* When analyzing runs */
            /* Then separate runs are created */
            var trades = List.of(
                    trade(100, "BUY", 100, 50.0),
                    trade(106, "BUY", 100, 50.1)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(2, result.runs().size());
        }

        @Test
        void givenGapExactly5s_whenAnalyze_thenSameRun() {
            /* Given trades with exactly 5 second gap */
            /* When analyzing runs */
            /* Then they stay in same run */
            var trades = List.of(
                    trade(100, "BUY", 100, 50.0),
                    trade(105, "BUY", 100, 50.1)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(1, result.runs().size());
        }
    }

    @Nested
    class ImpactBps {
        @Test
        void givenBuyRunRisingPrices_whenComputeImpact_thenPositiveBps() {
            /* Given BUY run with rising prices */
            /* When computing impact */
            /* Then impact is positive (last - first / first * 10000) */
            var trades = List.of(
                    trade(100, "BUY", 100, 100.0),
                    trade(101, "BUY", 100, 100.5)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(50.0, result.runs().get(0).impactBps(), 0.01);
        }

        @Test
        void givenSellRunFallingPrices_whenComputeImpact_thenPositiveBps() {
            /* Given SELL run with falling prices */
            /* When computing impact */
            /* Then impact is positive (first - last / first * 10000) */
            var trades = List.of(
                    trade(100, "SELL", 100, 100.0),
                    trade(101, "SELL", 100, 99.5)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(50.0, result.runs().get(0).impactBps(), 0.01);
        }

        @Test
        void givenZeroFirstPrice_whenComputeImpact_thenZero() {
            /* Given a trade with zero price */
            /* When computing impact */
            /* Then impact is zero */
            var trades = List.of(trade(100, "BUY", 100, 0.0));
            var result = service.analyzeRuns(trades);

            assertEquals(0.0, result.runs().get(0).impactBps(), 0.01);
        }
    }

    @Nested
    class RunTransitions {
        @Test
        void givenMultipleSideChanges_whenAnalyze_thenCorrectTransitions() {
            /* Given BUY->SELL->BUY sequence */
            /* When analyzing runs */
            /* Then passiveToAggressive and aggressiveToPassive are counted */
            var trades = List.of(
                    trade(100, "BUY", 100, 50.0),
                    trade(101, "SELL", 100, 49.0),
                    trade(102, "BUY", 100, 50.0)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(3, result.runs().size());
            assertEquals(1, result.transitions().passiveToAggressive());
            assertEquals(1, result.transitions().aggressiveToPassive());
            assertEquals(0, result.transitions().sameSide());
        }

        @Test
        void givenSameSideRuns_whenAnalyze_thenSameSideTransitions() {
            /* Given runs separated by gaps on same side */
            /* When analyzing runs */
            /* Then sameSide transition count is correct */
            var trades = List.of(
                    trade(100, "BUY", 100, 50.0),
                    trade(200, "BUY", 100, 50.0)
            );
            var result = service.analyzeRuns(trades);

            assertEquals(2, result.runs().size());
            assertEquals(1, result.transitions().sameSide());
        }
    }
}
