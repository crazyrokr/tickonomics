package com.tickonomics.computation.backtest;

import com.tickonomics.persistence.entity.TickData;
import java.util.List;
import java.util.Map;

/**
 * Produces the named indicator values a strategy reads from its input map, computed from the price
 * series up to and including {@code currentIndex}. Keys are present only when enough data exists to
 * compute a meaningful value, so strategies that null-check a key treat absent indicators as
 * neutral rather than receiving garbage.
 */
@FunctionalInterface
public interface IndicatorComputer {
    Map<String, Double> compute(List<TickData> ticks, int currentIndex);
}
