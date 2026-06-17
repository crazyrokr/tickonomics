package com.tickonomics.ingestion.equity;

import tools.jackson.databind.JsonNode;
import com.tickonomics.cdm.adapter.raw.FinnhubQuote;
import com.tickonomics.cdm.adapter.raw.YahooOhlcv;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Primary equity price client using Yahoo Finance v8 chart API. Fetches historical OHLCV bars via
 * the unofficial REST endpoint. No API key required — uses session cookie-based auth.
 */
@Component
@ConditionalOnProperty(name = "monitor.yahoo-finance.enabled", havingValue = "true", matchIfMissing = true)
public class YahooFinanceClient implements EquityPriceClient {

  private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);

  private final RestClient restClient;
  private final String baseUrl;
  private final RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
      .includes(RestClientException.class)
      .maxRetries(2)
      .delay(Duration.ofMillis(2000))
      .multiplier(2)
      .build());

  public YahooFinanceClient(
      RestClient.Builder restClientBuilder,
      @Value("${monitor.yahoo-finance.base-url:https://query1.finance.yahoo.com}") String baseUrl) {
    this.restClient = restClientBuilder.build();
    this.baseUrl = baseUrl;
  }

  @Override
  public List<YahooOhlcv> fetchHistoricalOhlcv(String symbol, String interval) {
    try {
      return retryTemplate.execute(() -> doFetchHistoricalOhlcv(symbol, interval));
    } catch (Exception e) {
      return fetchHistoricalOhlcvFallback(symbol, interval, e);
    }
  }

  private List<YahooOhlcv> doFetchHistoricalOhlcv(String symbol, String interval) {
    String url = baseUrl + "/v8/finance/chart/{symbol}?interval={interval}&range=5d";
    var requestHeadersUriSpec = restClient.get();
    var requestHeadersSpec = requestHeadersUriSpec
        .uri(url, symbol, interval);
    var response = requestHeadersSpec
        .retrieve()
        .body(JsonNode.class);

    if (response == null) {
      return List.of();
    }

    return parseChartResponse(response, symbol);
  }

  @Override
  public Optional<FinnhubQuote> fetchQuote(String symbol) {
    List<YahooOhlcv> bars = fetchHistoricalOhlcv(symbol, "1m");
    if (bars.isEmpty()) {
      return Optional.empty();
    }
    YahooOhlcv latest = bars.get(0);
    return Optional.of(new FinnhubQuote(latest.time(), latest.symbol(), latest.close(),
        latest.high(), latest.low(), latest.open(), latest.close(), latest.volume()));
  }

  @Override
  public String sourceName() {
    return "YAHOO_FINANCE";
  }

  List<YahooOhlcv> fetchHistoricalOhlcvFallback(String symbol, String interval, Throwable t) {
    log.warn("Yahoo Finance fetch failed for symbol {}: {}", symbol, t.getMessage());
    return List.of();
  }

  private List<YahooOhlcv> parseChartResponse(JsonNode response, String symbol) {
    JsonNode result = response.path("chart").path("result");
    if (!result.isArray() || result.isEmpty()) {
      return List.of();
    }

    JsonNode meta = result.get(0).path("meta");
    String resolvedSymbol = meta.has("symbol") ? meta.get("symbol").asText() : symbol;

    JsonNode timestamps = result.get(0).path("timestamp");
    JsonNode indicators = result.get(0).path("indicators").path("quote");

    if (!indicators.isArray() || indicators.isEmpty()) {
      return List.of();
    }

    JsonNode opens = indicators.get(0).path("open");
    JsonNode highs = indicators.get(0).path("high");
    JsonNode lows = indicators.get(0).path("low");
    JsonNode closes = indicators.get(0).path("close");
    JsonNode volumes = indicators.get(0).path("volume");

    if (!timestamps.isArray()) {
      return List.of();
    }

    List<YahooOhlcv> bars = new ArrayList<>();
    for (int i = 0; i < timestamps.size(); i++) {
      double close = closes.path(i).asDouble(Double.NaN);
      if (Double.isNaN(close) || close <= 0) {
        continue;
      }

      Instant time = Instant.ofEpochSecond(timestamps.get(i).asLong());
      double open = opens.path(i).asDouble(0);
      double high = highs.path(i).asDouble(0);
      double low = lows.path(i).asDouble(0);
      long volume = volumes.path(i).asLong(0);

      bars.add(new YahooOhlcv(time, resolvedSymbol, open, high, low, close, volume));
    }

    return bars;
  }
}
