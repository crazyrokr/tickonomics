package com.tickonomics.computation.backtest;

import com.tickonomics.persistence.entity.TickData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * IndicatorComputer backed by standard price-series indicators derivable from OHLC/tick data:
 * price, 1-bar return, fast/slow EMA, Wilder RSI, return volatility, Bollinger bands, rolling
 * high/low, and volume. Covers the momentum, mean-reversion, breakout, and volatility equity
 * strategies. Strategies needing indicators outside this set still receive null for those keys and
 * return neutral.
 */
@Component
public class PriceBasedIndicatorComputer implements IndicatorComputer {

    static final int EMA_FAST_PERIOD = 12;
    static final int EMA_SLOW_PERIOD = 26;
    static final int RSI_PERIOD = 14;
    static final int LOOKBACK = 20;
    static final double BOLLINGER_MULT = 2.0;

    @Override
    public Map<String, Double> compute(List<TickData> ticks, int currentIndex) {
        Map<String, Double> indicators = new LinkedHashMap<>();
        if (ticks == null || ticks.isEmpty() || currentIndex < 0 || currentIndex >= ticks.size()) {
            return indicators;
        }

        double price = ticks.get(currentIndex).price();
        indicators.put("price", price);

        if (currentIndex >= 1) {
            double previous = ticks.get(currentIndex - 1).price();
            if (previous != 0.0) {
                indicators.put("priceChange", (price - previous) / previous);
            }
        }

        putIfFinite(indicators, "emaFast", ema(ticks, currentIndex, EMA_FAST_PERIOD));
        putIfFinite(indicators, "emaSlow", ema(ticks, currentIndex, EMA_SLOW_PERIOD));
        putIfFinite(indicators, "rsi", rsi(ticks, currentIndex, RSI_PERIOD));
        putIfFinite(indicators, "sigma", returnsStd(ticks, currentIndex, LOOKBACK));

        double[] bollinger = bollinger(ticks, currentIndex, LOOKBACK, BOLLINGER_MULT);
        if (bollinger != null) {
            indicators.put("upperBand", bollinger[0]);
            indicators.put("lowerBand", bollinger[1]);
            if (bollinger[2] > 0.0) {
                indicators.put("bandwidth", (bollinger[0] - bollinger[1]) / bollinger[2]);
            }
        }

        indicators.put("volumeCurrent", (double) ticks.get(currentIndex).volume());
        putIfFinite(indicators, "volumeAvg", volumeAvg(ticks, currentIndex, LOOKBACK));
        indicators.put("high20", high(ticks, currentIndex, LOOKBACK));
        indicators.put("low20", low(ticks, currentIndex, LOOKBACK));

        return indicators;
    }

    private static void putIfFinite(Map<String, Double> target, String key, double value) {
        if (Double.isFinite(value)) {
            target.put(key, value);
        }
    }

    static double ema(List<TickData> ticks, int index, int period) {
        if (index + 1 < period) {
            return Double.NaN;
        }
        double k = 2.0 / (period + 1);
        double ema = 0.0;
        for (int i = 0; i < period; i++) {
            ema += ticks.get(i).price();
        }
        ema /= period;
        for (int i = period; i <= index; i++) {
            ema = ticks.get(i).price() * k + ema * (1.0 - k);
        }
        return ema;
    }

    static double rsi(List<TickData> ticks, int index, int period) {
        if (index < period) {
            return Double.NaN;
        }
        double gainSum = 0.0;
        double lossSum = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = ticks.get(i).price() - ticks.get(i - 1).price();
            if (change >= 0.0) {
                gainSum += change;
            } else {
                lossSum -= change;
            }
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        for (int i = period + 1; i <= index; i++) {
            double change = ticks.get(i).price() - ticks.get(i - 1).price();
            double gain = change >= 0.0 ? change : 0.0;
            double loss = change < 0.0 ? -change : 0.0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }
        if (avgLoss == 0.0) {
            return 100.0;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - 100.0 / (1.0 + rs);
    }

    static double returnsStd(List<TickData> ticks, int index, int lookback) {
        if (index < 2) {
            return Double.NaN;
        }
        int start = Math.max(1, index - lookback + 1);
        int count = index - start + 1;
        double sum = 0.0;
        double[] returns = new double[count];
        for (int i = start; i <= index; i++) {
            double previous = ticks.get(i - 1).price();
            double ret = previous != 0.0 ? (ticks.get(i).price() - previous) / previous : 0.0;
            returns[i - start] = ret;
            sum += ret;
        }
        double mean = sum / count;
        double variance = 0.0;
        for (double ret : returns) {
            variance += (ret - mean) * (ret - mean);
        }
        return Math.sqrt(variance / count);
    }

    static double[] bollinger(List<TickData> ticks, int index, int lookback, double mult) {
        int start = index - lookback + 1;
        if (start < 0) {
            return null;
        }
        double sum = 0.0;
        for (int i = start; i <= index; i++) {
            sum += ticks.get(i).price();
        }
        double sma = sum / lookback;
        double squared = 0.0;
        for (int i = start; i <= index; i++) {
            squared += Math.pow(ticks.get(i).price() - sma, 2);
        }
        double sd = Math.sqrt(squared / lookback);
        return new double[] {sma + mult * sd, sma - mult * sd, sma};
    }

    static double volumeAvg(List<TickData> ticks, int index, int lookback) {
        int start = Math.max(0, index - lookback + 1);
        if (start > index) {
            return Double.NaN;
        }
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= index; i++) {
            sum += ticks.get(i).volume();
            count++;
        }
        return count > 0 ? sum / count : Double.NaN;
    }

    static double high(List<TickData> ticks, int index, int lookback) {
        int start = Math.max(0, index - lookback + 1);
        double max = Double.NEGATIVE_INFINITY;
        for (int i = start; i <= index; i++) {
            max = Math.max(max, ticks.get(i).price());
        }
        return max;
    }

    static double low(List<TickData> ticks, int index, int lookback) {
        int start = Math.max(0, index - lookback + 1);
        double min = Double.POSITIVE_INFINITY;
        for (int i = start; i <= index; i++) {
            min = Math.min(min, ticks.get(i).price());
        }
        return min;
    }
}
