package com.tickonomics.ingestion.equity;

import com.tickonomics.cdm.adapter.raw.FinnhubQuote;
import com.tickonomics.cdm.adapter.raw.YahooOhlcv;

import java.util.List;

/**
 * Abstraction over equity price data sources. Implementations fetch OHLCV or quote data from
 * specific providers (Yahoo Finance, Finnhub) and return source-agnostic raw records.
 */
public interface EquityPriceClient {

  /**
   * Fetches historical OHLCV bars for the given symbol.
   *
   * @param symbol equity symbol (e.g., "SPY")
   * @param interval bar interval (e.g., "1m", "5m", "1h", "1d")
   * @return list of OHLCV data points, newest first
   */
  List<YahooOhlcv> fetchHistoricalOhlcv(String symbol, String interval);

  /**
   * Fetches a real-time quote snapshot for the given symbol.
   *
   * @param symbol equity symbol
   * @return current quote, or null if unavailable
   */
  FinnhubQuote fetchQuote(String symbol);

  /**
   * Returns the name of this data source (for logging and circuit-breaker identification).
   */
  String sourceName();
}
