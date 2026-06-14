package com.tickonomics.contracts.client;

import com.tickonomics.cdm.adapter.raw.FinnhubTrade;

import java.util.function.Consumer;

/**
 * Source-agnostic WebSocket client for real-time equity trade data. Implementations connect to
 * provider-specific WebSocket feeds (Finnhub, Polygon, etc.) and emit normalized trade events.
 */
public interface EquityWsClient {

  void connect(String apiKey);

  void subscribe(String symbol);

  void unsubscribe(String symbol);

  void onTrade(Consumer<FinnhubTrade> handler);

  void disconnect();

  boolean isConnected();
}
