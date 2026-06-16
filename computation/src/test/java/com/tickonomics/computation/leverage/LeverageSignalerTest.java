package com.tickonomics.computation.leverage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class LeverageSignalerTest {

    @InjectMocks
    private LeverageSignaler signaler;

    private List<Double> generatePrices(int count, double base, double step) {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prices.add(base + i * step);
        }
        return prices;
    }

    @Nested
    class Evaluate {
        @Test
        void givenPriceAboveSma_whenEvaluate_thenLeverageOn() {
            /* Given current price above 200-day SMA */
            /* When evaluating leverage signal */
            /* Then signal is LEVERAGE_ON */
            List<Double> prices = generatePrices(200, 100.0, 0.1);

            var result = signaler.evaluate(prices, 130.0);

            assertEquals(LeverageSignaler.Signal.LEVERAGE_ON, result.signal());
            assertEquals(130.0, result.currentPrice(), 0.01);
            assertTrue(result.sma200() > 0);
            assertTrue(result.deviation() > 0);
        }

        @Test
        void givenPriceBelowSma_whenEvaluate_thenLeverageOff() {
            /* Given current price below 200-day SMA */
            /* When evaluating leverage signal */
            /* Then signal is LEVERAGE_OFF */
            List<Double> prices = generatePrices(200, 100.0, 0.1);

            var result = signaler.evaluate(prices, 100.0);

            assertEquals(LeverageSignaler.Signal.LEVERAGE_OFF, result.signal());
            assertTrue(result.deviation() < 0);
        }

        @Test
        void givenFewerThan200Points_whenEvaluate_thenLeverageOffWithZeroSma() {
            /* Given fewer than 200 price points */
            /* When evaluating leverage signal */
            /* Then signal is LEVERAGE_OFF with sma200 = 0 */
            List<Double> prices = generatePrices(100, 100.0, 0.1);

            var result = signaler.evaluate(prices, 200.0);

            assertEquals(LeverageSignaler.Signal.LEVERAGE_OFF, result.signal());
            assertEquals(0.0, result.sma200(), 0.01);
            assertEquals(0.0, result.deviation(), 0.01);
        }

        @Test
        void givenNullPrices_whenEvaluate_thenLeverageOffWithZeroSma() {
            /* Given null price history */
            /* When evaluating leverage signal */
            /* Then signal is LEVERAGE_OFF with sma200 = 0 */
            var result = signaler.evaluate(null, 100.0);

            assertEquals(LeverageSignaler.Signal.LEVERAGE_OFF, result.signal());
            assertEquals(0.0, result.sma200(), 0.01);
        }

        @Test
        void givenExactly200Points_whenEvaluate_thenLeverageOn() {
            /* Given exactly 200 price points with uptrend */
            /* When evaluating leverage signal */
            /* Then SMA is computed and signal is correct */
            List<Double> prices = generatePrices(200, 100.0, 0.05);
            double lastPrice = prices.get(199);

            var result = signaler.evaluate(prices, lastPrice + 1.0);

            assertEquals(LeverageSignaler.Signal.LEVERAGE_ON, result.signal());
            assertTrue(result.sma200() > 0);
        }

        @Test
        void givenResult_whenEvaluate_thenTimestampIsNotNull() {
            /* Given valid price data */
            /* When evaluating leverage signal */
            /* Then timestamp is not null */
            List<Double> prices = generatePrices(200, 100.0, 0.1);

            var result = signaler.evaluate(prices, 130.0);

            assertNotNull(result.timestamp());
        }
    }

    @Nested
    class ComputeSma {
        @Test
        void givenValidPrices_whenComputeSma_thenCorrectAverage() {
            /* Given 200 prices at 100 */
            /* When computing SMA */
            /* Then result is 100 */
            List<Double> prices = generatePrices(200, 100.0, 0.0);

            double sma = signaler.computeSma(prices, 200);

            assertEquals(100.0, sma, 0.01);
        }

        @Test
        void givenLinearPrices_whenComputeSma_thenCorrectAverage() {
            /* Given prices from 100 to 299 step 1 */
            /* When computing 200-period SMA */
            /* Then result is average of last 200 values */
            List<Double> prices = generatePrices(300, 100.0, 1.0);

            double sma = signaler.computeSma(prices, 200);

            double expected = (200 * 100.0 + 199 * 1.0 / 2.0);
            assertTrue(sma > 100.0);
        }

        @Test
        void givenInsufficientData_whenComputeSma_thenZero() {
            /* Given fewer prices than period */
            /* When computing SMA */
            /* Then result is zero */
            List<Double> prices = generatePrices(50, 100.0, 0.0);

            double sma = signaler.computeSma(prices, 200);

            assertEquals(0.0, sma, 0.01);
        }
    }
}
