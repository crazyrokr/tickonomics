package com.tickonomics.computation.leverage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LeverageSignaler {

    private static final Logger log = LoggerFactory.getLogger(LeverageSignaler.class);
    private static final int SMA_PERIOD = 200;

    public enum Signal {
        LEVERAGE_ON, LEVERAGE_OFF
    }

    public record LeverageSignal(Signal signal, double currentPrice, double sma200,
                                 double deviation, Instant timestamp) {
    }

    public LeverageSignal evaluate(List<Double> priceHistory, double currentPrice) {
        Instant timestamp = Instant.now();

        if (priceHistory == null || priceHistory.size() < SMA_PERIOD) {
            log.debug("Insufficient data for SMA200: {} prices, need {}",
                    priceHistory != null ? priceHistory.size() : 0, SMA_PERIOD);
            return new LeverageSignal(Signal.LEVERAGE_OFF, currentPrice, 0.0, 0.0, timestamp);
        }

        double sma200 = computeSma(priceHistory, SMA_PERIOD);
        double deviation = sma200 > 0 ? (currentPrice - sma200) / sma200 : 0.0;

        Signal signal = currentPrice > sma200 ? Signal.LEVERAGE_ON : Signal.LEVERAGE_OFF;

        log.debug("Leverage signal: {} price={:.2f} sma200={:.2f} deviation={:.4f}",
                signal, currentPrice, sma200, deviation);

        return new LeverageSignal(signal, currentPrice, sma200, deviation, timestamp);
    }

    double computeSma(List<Double> prices, int period) {
        if (prices.size() < period) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }
}
