package com.tickonomics.contracts.client;

import com.tickonomics.cdm.adapter.raw.PolygonTick;

import java.util.function.Consumer;

public interface PolygonWsClient {

  void connect(String apiKey);

  void subscribe(String symbol);

  void unsubscribe(String symbol);

  void onTick(Consumer<PolygonTick> handler);

  void disconnect();

  boolean isConnected();
}
