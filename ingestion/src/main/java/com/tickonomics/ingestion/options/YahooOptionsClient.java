package com.tickonomics.ingestion.options;

import com.fasterxml.jackson.databind.JsonNode;
import com.tickonomics.cdm.adapter.YahooOptionsCdmAdapter;
import com.tickonomics.cdm.adapter.raw.YahooOptionContract;
import com.tickonomics.cdm.model.CdmOptionSnapshot;
import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance options chain client. Replaces the paid Polygon.io options REST endpoint with the
 * free Yahoo Finance v7 options API. Fetches full options chains including strikes, expiry, bid/ask,
 * IV, OI, volume, and Greeks.
 */
@Component
@ConditionalOnProperty(name = "monitor.yahoo-finance.options-enabled", havingValue = "true", matchIfMissing = false)
public class YahooOptionsClient {

  private static final Logger log = LoggerFactory.getLogger(YahooOptionsClient.class);

  private final RestClient restClient;
  private final YahooOptionsCdmAdapter cdmAdapter;

  @Value("${monitor.yahoo-finance.base-url:https://query1.finance.yahoo.com}")
  private String baseUrl;

  @Value("${monitor.yahoo-finance.options-symbols:SPY,QQQ}")
  private List<String> optionsSymbols;

  public YahooOptionsClient(RestClient.Builder restClientBuilder, YahooOptionsCdmAdapter cdmAdapter) {
    this.restClient = restClientBuilder.build();
    this.cdmAdapter = cdmAdapter;
  }

  @Scheduled(fixedDelayString = "${monitor.yahoo-finance.options-poll-interval-ms:3600000}")
  @Retry(name = "yahooFinanceApi")
  public void pollOptionsSnapshots() {
    for (String symbol : optionsSymbols) {
      try {
        List<YahooOptionContract> contracts = fetchOptionsChain(symbol);
        log.info("Fetched {} option contracts for {}", contracts.size(), symbol);
      } catch (Exception e) {
        log.error("Failed to fetch options for {}: {}", symbol, e.getMessage());
      }
    }
  }

  public List<YahooOptionContract> fetchOptionsChain(String symbol) {
    JsonNode response = fetchOptionsResponse(symbol);
    if (response == null) {
      return List.of();
    }

    JsonNode options = response.path("optionChain").path("options");
    if (!options.isArray() || options.isEmpty()) {
      return List.of();
    }

    List<YahooOptionContract> contracts = new ArrayList<>();
    for (JsonNode optionGroup : options) {
      parseOptionList(optionGroup.path("calls"), symbol, "CALL", contracts);
      parseOptionList(optionGroup.path("puts"), symbol, "PUT", contracts);
    }

    return contracts;
  }

  private JsonNode fetchOptionsResponse(String symbol) {
    String url = baseUrl + "/v7/finance/options/{symbol}";
    return restClient.get()
        .uri(url, symbol)
        .retrieve()
        .body(JsonNode.class);
  }

  private void parseOptionList(JsonNode optionsList, String underlying, String optionType,
      List<YahooOptionContract> contracts) {
    if (!optionsList.isArray()) {
      return;
    }

    for (JsonNode option : optionsList) {
      double bid = option.path("bid").asDouble(0);
      double ask = option.path("ask").asDouble(0);
      if (bid <= 0 && ask <= 0) {
        continue;
      }

      BigDecimal strike = BigDecimal.valueOf(option.path("strike").asDouble());
      long expiryEpoch = option.path("expiration").asLong();
      LocalDate expiry = LocalDate.ofEpochDay(expiryEpoch / 86400);

      contracts.add(new YahooOptionContract(
          Instant.now(), underlying, strike, expiry, optionType,
          bid, ask,
          option.path("lastPrice").asDouble(0),
          option.path("impliedVolatility").asDouble(0),
          option.path("delta").asDouble(0),
          option.path("gamma").asDouble(0),
          option.path("theta").asDouble(0),
          option.path("vega").asDouble(0),
          option.path("rho").asDouble(0),
          option.path("openInterest").asLong(0),
          option.path("underlyingPrice").asDouble(0)));
    }
  }
}
