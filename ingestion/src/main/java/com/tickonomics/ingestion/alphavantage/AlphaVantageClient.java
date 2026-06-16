package com.tickonomics.ingestion.alphavantage;

import tools.jackson.databind.JsonNode;
import com.tickonomics.cdm.adapter.AlphaVantageCdmAdapter;
import com.tickonomics.cdm.adapter.raw.AlphaVantageDailyBar;
import com.tickonomics.cdm.model.CdmTick;
import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import com.tickonomics.persistence.entity.TickData;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Alpha Vantage REST client for deep historical equity OHLCV (20+ years, split/dividend adjusted)
 * and commodity daily prices. Enforces rate limiting with configurable inter-call delay (default
 * 12.1s, staying within the 5 req/min free tier cap).
 */
@Component
@ConditionalOnProperty(name = "monitor.alpha-vantage.enabled", havingValue = "true", matchIfMissing = false)
public class AlphaVantageClient {

  private static final Logger log = LoggerFactory.getLogger(AlphaVantageClient.class);
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final RestClient restClient;
  private final AlphaVantageCdmAdapter cdmAdapter;
  private final TimescaleDbWriter writer;

  @Value("${monitor.alpha-vantage.base-url:https://www.alphavantage.co/query}")
  private String baseUrl;

  @Value("${monitor.alpha-vantage.api-key:}")
  private String apiKey;

  @Value("${monitor.alpha-vantage.rate-limit-delay-ms:12100}")
  private long rateLimitDelayMs;

  @Value("${monitor.alpha-vantage.symbols:SPY,QQQ,IWM,TLT,HYG,GLD}")
  private List<String> symbols;

  @Value("${monitor.alpha-vantage.commodities:GOLD,OIL,NATURAL_GAS,COPPER}")
  private List<String> commodities;

  public AlphaVantageClient(
      RestClient.Builder restClientBuilder,
      AlphaVantageCdmAdapter cdmAdapter,
      TimescaleDbWriter writer) {
    this.restClient = restClientBuilder.build();
    this.cdmAdapter = cdmAdapter;
    this.writer = writer;
  }

  @Retryable(includes = RestClientException.class, maxRetries = 2, delay = 12000, multiplier = 2)
  public List<AlphaVantageDailyBar> fetchAdjustedDaily(String symbol, boolean fullOutput) {
    String outputSize = fullOutput ? "full" : "compact";
    String url = baseUrl + "?function=TIME_SERIES_DAILY_ADJUSTED&symbol={symbol}&outputsize={size}&apikey={key}";

    var response = restClient.get()
        .uri(url, symbol, outputSize, apiKey)
        .retrieve()
        .body(JsonNode.class);

    if (response == null) {
      return List.of();
    }

    JsonNode timeSeries = response.path("Time Series (Daily)");
    if (!timeSeries.isObject()) {
      String info = response.path("Information").asText("");
      if (!info.isEmpty()) {
        log.warn("Alpha Vantage API message for {}: {}", symbol, info);
      }
      return List.of();
    }

    List<AlphaVantageDailyBar> bars = new ArrayList<>();
    timeSeries.properties().forEach(entry -> {
      String dateStr = entry.getKey();
      JsonNode fields = entry.getValue();

      double adjustedClose = fields.path("5. adjusted close").asDouble(0);
      if (adjustedClose <= 0) {
        return;
      }

      Instant time = LocalDate.parse(dateStr, DATE_FMT)
          .atStartOfDay().toInstant(ZoneOffset.UTC);

      bars.add(new AlphaVantageDailyBar(
          time, symbol,
          fields.path("1. open").asDouble(0),
          fields.path("2. high").asDouble(0),
          fields.path("3. low").asDouble(0),
          fields.path("4. close").asDouble(0),
          adjustedClose,
          fields.path("6. volume").asLong(0),
          fields.path("7. dividend amount").asDouble(0),
          fields.path("8. split coefficient").asDouble(1)));
    });

    log.info("Fetched {} adjusted daily bars for {}", bars.size(), symbol);
    return bars;
  }

  @Retryable(includes = RestClientException.class, maxRetries = 2, delay = 12000, multiplier = 2)
  public List<AlphaVantageDailyBar> fetchCommodity(String commodity) {
    String url = baseUrl + "?function={function}&interval=daily&apikey={key}";
    String function = switch (commodity.toUpperCase()) {
      case "GOLD" -> "COMMODITY_DAILY";
      case "OIL" -> "COMMODITY_DAILY";
      case "NATURAL_GAS" -> "COMMODITY_DAILY";
      case "COPPER" -> "COMMODITY_DAILY";
      default -> "COMMODITY_DAILY";
    };

    var response = restClient.get()
        .uri(url, function, apiKey)
        .retrieve()
        .body(JsonNode.class);

    if (response == null) {
      return List.of();
    }

    JsonNode data = response.path("data");
    if (!data.isArray()) {
      return List.of();
    }

    List<AlphaVantageDailyBar> bars = new ArrayList<>();
    for (JsonNode item : data) {
      double value = item.path("value").asDouble(0);
      if (value <= 0) {
        continue;
      }

      String dateStr = item.path("date").asText("");
      if (dateStr.isEmpty()) {
        continue;
      }

      Instant time = LocalDate.parse(dateStr, DATE_FMT)
          .atStartOfDay().toInstant(ZoneOffset.UTC);

      bars.add(new AlphaVantageDailyBar(time, commodity, value, value, value, value, value, 0, 0, 1));
    }

    log.info("Fetched {} commodity bars for {}", bars.size(), commodity);
    return bars;
  }

  public void fetchAndWriteSymbol(String symbol, boolean fullOutput) {
    List<AlphaVantageDailyBar> bars = fetchAdjustedDaily(symbol, fullOutput);
    for (AlphaVantageDailyBar bar : bars) {
      CdmTick tick = cdmAdapter.toCdm(bar);
      writer.writeTick(toEntity(tick));
    }
    rateLimitSleep();
  }

  public void fetchAndWriteCommodity(String commodity) {
    List<AlphaVantageDailyBar> bars = fetchCommodity(commodity);
    for (AlphaVantageDailyBar bar : bars) {
      CdmTick tick = cdmAdapter.toCdm(bar);
      writer.writeTick(toEntity(tick));
    }
    rateLimitSleep();
  }

  List<String> getSymbols() {
    return symbols;
  }

  List<String> getCommodities() {
    return commodities;
  }

  private void rateLimitSleep() {
    try {
      Thread.sleep(rateLimitDelayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private TickData toEntity(CdmTick cdm) {
    return new TickData(cdm.time(), cdm.symbol(), cdm.price(), cdm.volume(), cdm.conditions());
  }
}
