package com.tickonomics.computation.equity;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class UniverseAggregator {

    private static final int MAX_SYMBOLS = 1000;

    private final AtomicReference<UniverseContext> currentContext = new AtomicReference<>();

    public void processTickCycle(Map<String, Double> symbolReturns) {
        if (symbolReturns.size() > MAX_SYMBOLS) {
            throw new IllegalArgumentException("Symbol count exceeds cap: " + symbolReturns.size());
        }

        double rBar = symbolReturns.values().stream()
            .mapToDouble(d -> d).average().orElse(0.0);

        double variance = symbolReturns.values().stream()
            .mapToDouble(r -> Math.pow(r - rBar, 2))
            .average().orElse(0.0);
        double sigmaR = Math.sqrt(variance);

        currentContext.set(new UniverseContext(Instant.now(), rBar, sigmaR, symbolReturns.size()));
    }

    public UniverseContext getContext() {
        return currentContext.get();
    }
}
