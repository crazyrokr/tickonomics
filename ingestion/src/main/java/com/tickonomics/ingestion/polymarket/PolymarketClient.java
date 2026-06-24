package com.tickonomics.ingestion.polymarket;

import com.tickonomics.cdm.adapter.PolymarketCdmAdapter;
import com.tickonomics.cdm.adapter.raw.PolymarketQuote;
import com.tickonomics.cdm.model.CdmPredictionMarketQuote;
import com.tickonomics.ingestion.tracing.IngestionTracer;
import com.tickonomics.ingestion.tracing.IngestionTracingConfig;
import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import com.tickonomics.persistence.entity.PredictionMarketQuote;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Polls Polymarket (Gamma public API) for prediction-market quotes and persists them through the CDM
 * adapter + {@link TimescaleDbWriter}. Disabled by default (ADR-037); enable via
 * {@code monitor.polymarket.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "monitor.polymarket.enabled", havingValue = "true", matchIfMissing = false)
public class PolymarketClient {

  private static final Logger log = LoggerFactory.getLogger(PolymarketClient.class);
  private static final Pattern FIRST_QUOTED_NUMBER = Pattern.compile("\"([0-9]*\\.?[0-9]+)\"");

  private final RestClient restClient;
  private final TimescaleDbWriter writer;
  private final PolymarketCdmAdapter cdmAdapter;
  private final IngestionTracer tracer;

  @Value("${monitor.polymarket.base-url:https://gamma-api.polymarket.com}")
  private String baseUrl;

  @Value("${monitor.polymarket.limit:100}")
  private int limit;

  public PolymarketClient(
      RestClient.Builder restClientBuilder,
      TimescaleDbWriter writer,
      PolymarketCdmAdapter cdmAdapter,
      IngestionTracer tracer) {
    this.restClient = restClientBuilder.build();
    this.writer = writer;
    this.cdmAdapter = cdmAdapter;
    this.tracer = tracer;
  }

  @Scheduled(fixedDelayString = "${monitor.polymarket.poll-interval-ms:300000}")
  @Bulkhead(name = "criticalIngestion")
  public void pollMarkets() {
    try {
      List<PolymarketMarketRaw> markets = fetchMarkets();
      Instant now = Instant.now();
      int written = 0;
      for (PolymarketMarketRaw market : markets) {
        try {
          PolymarketQuote raw = toQuote(market, now);
          if (raw == null) {
            continue;
          }
          CdmPredictionMarketQuote cdm = cdmAdapter.toCdm(raw);
          writer.writePredictionMarketQuote(toEntity(cdm));
          written++;
        } catch (Exception e) {
          log.debug("Skipping Polymarket market: {}", e.getMessage());
        }
      }
      log.info("Fetched {} Polymarket markets, wrote {} quotes", markets.size(), written);
    } catch (Exception e) {
      log.error("Failed to poll Polymarket markets: {}", e.getMessage());
    }
  }

  @Retryable(includes = RestClientException.class, maxRetries = 2, delay = 1000, multiplier = 2)
  public List<PolymarketMarketRaw> fetchMarkets() {
    String url = baseUrl + "/markets?limit={limit}&active=true&closed=false&order=volumeNum&ascending=false";
    try (var scope = tracer.span(IngestionTracingConfig.SPAN_POLYMARKET_FETCH)) {
      List<PolymarketMarketRaw> markets = restClient
          .get()
          .uri(url, limit)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {});
      return markets != null ? markets : List.of();
    }
  }

  /**
   * Maps a raw Gamma market to a {@link PolymarketQuote}. Returns {@code null} when the market lacks
   * a parseable yes-outcome price, which keeps malformed payloads out of the pipeline.
   */
  static PolymarketQuote toQuote(PolymarketMarketRaw market, Instant time) {
    if (market == null) {
      return null;
    }
    double yesPrice = firstQuotedNumber(market.outcomePrices());
    if (Double.isNaN(yesPrice)) {
      return null;
    }
    double volume = parseLenient(market.volume());
    double liquidity = parseLenient(market.liquidity());
    return new PolymarketQuote(
        time, market.id(), market.slug(), market.question(), yesPrice, volume, liquidity, "POLYMARKET");
  }

  private PredictionMarketQuote toEntity(CdmPredictionMarketQuote cdm) {
    return new PredictionMarketQuote(
        cdm.time(), cdm.marketId(), cdm.question(),
        cdm.outcomeYesPrice(), cdm.volume(), cdm.liquidity(), cdm.source());
  }

  private static double firstQuotedNumber(String outcomePrices) {
    if (outcomePrices == null || outcomePrices.isBlank()) {
      return Double.NaN;
    }
    Matcher matcher = FIRST_QUOTED_NUMBER.matcher(outcomePrices);
    if (!matcher.find()) {
      return Double.NaN;
    }
    return Double.parseDouble(matcher.group(1));
  }

  private static double parseLenient(String value) {
    if (value == null || value.isBlank()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  record PolymarketMarketRaw(
      String id, String slug, String question, String outcomePrices,
      String volume, String liquidity, Boolean active, Boolean closed) {}
}
