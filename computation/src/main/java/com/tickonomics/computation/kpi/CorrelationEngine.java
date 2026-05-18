package com.tickonomics.computation.kpi;

import com.tickonomics.computation.talib.TalibAdapter;
import com.tickonomics.persistence.entity.CorrelationOutput;
import com.tickonomics.persistence.repository.CorrelationOutputRepository;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
import com.tickonomics.persistence.repository.TickDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngine.class);
    private static final int LIVE_WINDOW_SIZE = 20;

    private final TalibAdapter talibAdapter;
    private final CorrelationOutputRepository correlationRepository;
    private final RateSnapshotRepository rateRepository;
    private final TickDataRepository tickRepository;

    public CorrelationEngine(TalibAdapter talibAdapter,
                             CorrelationOutputRepository correlationRepository,
                             RateSnapshotRepository rateRepository,
                             TickDataRepository tickRepository) {
        this.talibAdapter = talibAdapter;
        this.correlationRepository = correlationRepository;
        this.rateRepository = rateRepository;
        this.tickRepository = tickRepository;
    }

    public CorrelationResult computeRollingCorrelation(String symbolX, String symbolY, int window) {
        double[] xSeries = getLatestValues(symbolX, window * 2);
        double[] ySeries = getLatestValues(symbolY, window * 2);

        if (xSeries.length < window || ySeries.length < window) {
            return CorrelationResult.insufficient(symbolX, symbolY, window);
        }

        int len = Math.min(xSeries.length, ySeries.length);
        double[] x = lastN(xSeries, len);
        double[] y = lastN(ySeries, len);

        double[] correlations = talibAdapter.computeCorrel(x, y, window);

        if (correlations.length == 0) {
            return CorrelationResult.insufficient(symbolX, symbolY, window);
        }

        double latest = correlations[correlations.length - 1];
        return new CorrelationResult(symbolX, symbolY, "PEARSON_CORRELATION",
                latest, window, correlations, true);
    }

    public CorrelationResult computeRollingBeta(String symbol, String benchmark, int window) {
        double[] symbolSeries = getLatestValues(symbol, window * 2);
        double[] benchmarkSeries = getLatestValues(benchmark, window * 2);

        if (symbolSeries.length < window || benchmarkSeries.length < window) {
            return CorrelationResult.insufficient(symbol, benchmark, window);
        }

        int len = Math.min(symbolSeries.length, benchmarkSeries.length);
        double[] s = lastN(symbolSeries, len);
        double[] b = lastN(benchmarkSeries, len);

        double[] betas = talibAdapter.computeBeta(s, b, window);

        if (betas.length == 0) {
            return CorrelationResult.insufficient(symbol, benchmark, window);
        }

        double latest = betas[betas.length - 1];
        return new CorrelationResult(symbol, benchmark, "OLS_BETA",
                latest, window, betas, true);
    }

    public void persistCorrelation(CorrelationResult result) {
        if (!result.valid()) return;
        correlationRepository.save(new CorrelationOutput(
                Instant.now(), result.symbolX(), result.metric(),
                result.latest(), null, result.window(), null, null));
    }

    double[] getLatestValues(String identifier, int count) {
        Instant to = Instant.now();
        Instant from = to.minus(java.time.Duration.ofDays(count));

        try {
            var rates = rateRepository.findByRateTypeAndTimeBetween(identifier, from, to);
            if (!rates.isEmpty()) {
                return rates.stream().mapToDouble(r -> r.value()).toArray();
            }
        } catch (Exception ignored) {}

        try {
            var ticks = tickRepository.findLatestBySymbol(identifier, count);
            if (!ticks.isEmpty()) {
                return ticks.stream().mapToDouble(t -> t.price()).toArray();
            }
        } catch (Exception ignored) {}

        return new double[0];
    }

    private double[] lastN(double[] values, int n) {
        if (values.length <= n) return values;
        double[] result = new double[n];
        System.arraycopy(values, values.length - n, result, 0, n);
        return result;
    }

    public static class CorrelationResult {
        private final String symbolX;
        private final String symbolY;
        private final String metric;
        private final double latest;
        private final int window;
        private final double[] series;
        private final boolean valid;

        private CorrelationResult(String symbolX, String symbolY, String metric,
                                  double latest, int window, double[] series, boolean valid) {
            this.symbolX = symbolX;
            this.symbolY = symbolY;
            this.metric = metric;
            this.latest = latest;
            this.window = window;
            this.series = series;
            this.valid = valid;
        }

        static CorrelationResult insufficient(String x, String y, int window) {
            return new CorrelationResult(x, y, "", Double.NaN, window, new double[0], false);
        }

        public String symbolX() { return symbolX; }
        public String symbolY() { return symbolY; }
        public String metric() { return metric; }
        public double latest() { return latest; }
        public int window() { return window; }
        public double[] series() {
            return series != null ? series.clone() : null;
        }
        public boolean valid() { return valid; }
    }
}
