package com.tickonomics.computation.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Read-only access to historical daily close prices. Backed in production by the
 * {@code ohlcv_1d} continuous aggregate; mocked in unit tests so signal-quality and leverage
 * computations stay decoupled from the persistence layer.
 */
public interface MarketPriceLookup {

  Optional<Double> closingPrice(String symbol, LocalDate day);

  List<Double> closingPrices(String symbol, LocalDate from, LocalDate toExclusive);
}
