package com.tickonomics.computation.equity;

import com.tickonomics.computation.util.StatisticsUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class UniverseAggregator {

  private static final int MAX_SYMBOLS = 1000;

  private final AtomicReference<UniverseContext> currentContext = new AtomicReference<>();

  public void processTickCycle(Map<String, Double> symbolReturns) {
    if (symbolReturns.size() > MAX_SYMBOLS) {
      throw new IllegalArgumentException("Symbol count exceeds cap: " + symbolReturns.size());
    }

    double rBar = StatisticsUtils.mean(symbolReturns.values());

    double sigmaR = StatisticsUtils.populationStdDev(symbolReturns.values(), rBar);

    currentContext.set(new UniverseContext(Instant.now(), rBar, sigmaR, symbolReturns.size()));
  }

  public UniverseContext getContext() {
    return currentContext.get();
  }
}
