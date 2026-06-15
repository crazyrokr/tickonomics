package com.tickonomics.ingestion.equity;

import com.fasterxml.jackson.databind.JsonNode;
import com.tickonomics.cdm.adapter.raw.FinnhubQuote;
import com.tickonomics.cdm.adapter.raw.YahooOhlcv;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/**
 * Fallback equity price client using Finnhub REST API. Provides real-time quotes and daily candle
 * data. Requires a free API key.
 */
@Component
@ConditionalOnProperty(name = "monitor.finnhub.rest-enabled", havingValue = "true", matchIfMissing = true)
public class FinnhubEquityClient implements EquityPriceClient {

  private static final Logger log = LoggerFactory.getLogger(FinnhubEquityClient.class);

  private final RestClient restClient;

  @Value("${monitor.finnhub.rest-url:https://finnhub.io/api/v1}")
  private String restUrl;

  private final String apiKey;

  public FinnhubEquityClient(
      RestClient.Builder restClientBuilder,
      @Value("${monitor.finnhub.api-key:}") String apiKey) {
    this.restClient = restClientBuilder.build();
    this.apiKey = apiKey;
  }

  @Override
  @Retry(name = "finnhubApi", fallbackMethod = "fetchHistoricalOhlcvFallback")
  public List<YahooOhlcv> fetchHistoricalOhlcv(String symbol, String interval) {
    long now = Instant.now().getEpochSecond();
    long from = now - 5 * 86400;
    String url = restUrl + "/stock/candle?symbol={symbol}&resolution=D&from={from}&to={to}&token={token}";

    var response = restClient.get()
        .uri(url, symbol, from, now, apiKey)
        .retrieve()
        .body(JsonNode.class);

    if (response == null || !"ok".equals(response.path("s").asText())) {
      return List.of();
    }

    JsonNode timestamps = response.path("t");
    JsonNode closes = response.path("c");
    JsonNode opens = response.path("o");
    JsonNode highs = response.path("h");
    JsonNode lows = response.path("l");
    JsonNode volumes = response.path("v");

    if (!timestamps.isArray()) {
      return List.of();
    }

    return new java.util.ArrayList<>() {{
      for (int i = 0; i < timestamps.size(); i++) {
        double close = closes.path(i).asDouble(Double.NaN);
        if (Double.isNaN(close) || close <= 0) {
          continue;
        }
        Instant time = Instant.ofEpochSecond(timestamps.get(i).asLong());
        add(new YahooOhlcv(time, symbol,
            opens.path(i).asDouble(0), highs.path(i).asDouble(0),
            lows.path(i).asDouble(0), close, volumes.path(i).asLong(0)));
      }
    }};
  }

  @Override
  @Retry(name = "finnhubApi", fallbackMethod = "fetchQuoteFallback")
  public FinnhubQuote fetchQuote(String symbol) {
    String url = restUrl + "/quote?symbol={symbol}&token={token}";
    var response = restClient.get()
        .uri(url, symbol, apiKey)
        .retrieve()
        .body(JsonNode.class);

    if (response == null || response.path("c").asDouble(0) <= 0) {
      return null;
    }

    return new FinnhubQuote(
        Instant.now(), symbol,
        response.path("c").asDouble(),
        response.path("h").asDouble(),
        response.path("l").asDouble(),
        response.path("o").asDouble(),
        response.path("pc").asDouble(),
        response.path("v").asLong(0));
  }

  @Override
  public String sourceName() {
    return "FINNHUB";
  }

  List<YahooOhlcv> fetchHistoricalOhlcvFallback(String symbol, String interval, Throwable t) {
    log.warn("Finnhub OHLCV fetch failed for {}: {}", symbol, t.getMessage());
    return List.of();
  }

  FinnhubQuote fetchQuoteFallback(String symbol, Throwable t) {
    log.warn("Finnhub quote fetch failed for {}: {}", symbol, t.getMessage());
    return null;
  }
}
