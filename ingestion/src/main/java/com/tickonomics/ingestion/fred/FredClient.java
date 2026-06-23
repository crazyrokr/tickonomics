package com.tickonomics.ingestion.fred;

import com.tickonomics.cdm.adapter.FredCdmAdapter;
import com.tickonomics.cdm.adapter.raw.FredObservation;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.ingestion.tracing.IngestionTracer;
import com.tickonomics.ingestion.tracing.IngestionTracingConfig;
import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import com.tickonomics.persistence.entity.RateSnapshot;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClientException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "monitor.fred.enabled", matchIfMissing = true)
public class FredClient {

  private static final Logger log = LoggerFactory.getLogger(FredClient.class);
  private static final DateTimeFormatter FRED_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final List<String> SERIES_IDS = List.of(
      "EFFR", "RRPONTSYD", "WTREGEN", "WALCL", "IORB",
      "DGS1MO", "DGS3MO", "DGS6MO", "DGS1", "DGS2", "DGS5", "DGS10", "DGS30");

  private final RestClient restClient;
  private final TimescaleDbWriter writer;
  private final FredCdmAdapter cdmAdapter;
  private final IngestionTracer tracer;

  @Value("${monitor.fred.api-key:}")
  private String apiKey;

  @Value("${monitor.fred.base-url:https://api.stlouisfed.org/fred}")
  private String baseUrl;

  public FredClient(
      RestClient.Builder restClientBuilder,
      TimescaleDbWriter writer,
      FredCdmAdapter cdmAdapter,
      IngestionTracer tracer) {
    this.restClient = restClientBuilder.build();
    this.writer = writer;
    this.cdmAdapter = cdmAdapter;
    this.tracer = tracer;
  }

  @Scheduled(fixedDelayString = "${monitor.fred.poll-interval-ms:300000}")
  @Bulkhead(name = "criticalIngestion")
  public void pollAllSeries() {
    for (String seriesId : SERIES_IDS) {
      try {
        var observations = fetchSeries(seriesId);
        for (var obs : observations) {
          var cdm = cdmAdapter.toCdm(obs);
          writer.writeRate(toEntity(cdm));
        }
        log.info("Fetched {} observations for series {}", observations.size(), seriesId);
      } catch (Exception e) {
        log.error("Failed to fetch FRED series {}: {}", seriesId, e.getMessage());
      }
    }
  }

  @Retryable(includes = RestClientException.class, maxRetries = 2, delay = 1000, multiplier = 2)
  public List<FredObservation> fetchSeries(String seriesId) {
    FredSeriesResponse response;
    try (var scope = tracer.span(IngestionTracingConfig.SPAN_FRED_FETCH)) {
      String url =
          baseUrl + "/series/observations?series_id={seriesId}&api_key={apiKey}&file_type=json&sort_order=desc&limit=10";
      response = restClient
          .get()
          .uri(url, seriesId, apiKey)
          .retrieve()
          .body(FredSeriesResponse.class);
    }

    if (response == null || response.observations() == null) {
      return List.of();
    }

    return response
        .observations()
        .stream()
        .filter(obs -> obs.value() != null && !".".equals(obs.value()))
        .map(obs -> new FredObservation(
            LocalDate
                .parse(obs.date(), FRED_DATE_FMT)
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC), seriesId, Double.parseDouble(obs.value()), "FRED"))
        .toList();
  }

  private RateSnapshot toEntity(CdmRateSnapshot cdm) {
    return new RateSnapshot(
        cdm.time(),
        cdm
            .instrumentType()
            .name(),
        cdm.value(),
        cdm.source(),
        null,
        null);
  }

  record FredSeriesResponse(List<FredObservationRaw> observations) {}

  record FredObservationRaw(String date, String value) {}
}
