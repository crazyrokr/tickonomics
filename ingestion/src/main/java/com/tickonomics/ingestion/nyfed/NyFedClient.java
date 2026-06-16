package com.tickonomics.ingestion.nyfed;

import com.tickonomics.cdm.adapter.NyFedCdmAdapter;
import com.tickonomics.cdm.adapter.raw.NyFedRateResponse;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NyFedClient {

  private static final Logger log = LoggerFactory.getLogger(NyFedClient.class);
  private static final DateTimeFormatter NYFED_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final List<String> RATE_TYPES = List.of("sofr", "tgcr", "bgcr");

  private final RestClient restClient;
  private final TimescaleDbWriter writer;
  private final NyFedCdmAdapter cdmAdapter;
  private final IngestionTracer tracer;

  @Value("${monitor.nyfed.base-url:https://markets.newyorkfed.org/api}")
  private String baseUrl;

  public NyFedClient(
      RestClient.Builder restClientBuilder,
      TimescaleDbWriter writer,
      NyFedCdmAdapter cdmAdapter,
      IngestionTracer tracer) {
    this.restClient = restClientBuilder.build();
    this.writer = writer;
    this.cdmAdapter = cdmAdapter;
    this.tracer = tracer;
  }

  @Scheduled(fixedDelayString = "${monitor.nyfed.poll-interval-ms:300000}")
  @Bulkhead(name = "criticalIngestion")
  public void pollAllRates() {
    for (String rateType : RATE_TYPES) {
      try {
        var responses = fetchRates(rateType);
        for (var resp : responses) {
          var cdm = cdmAdapter.toCdm(resp);
          writer.writeRate(toEntity(cdm));
        }
        log.info("Fetched {} observations for NY Fed rate {}", responses.size(), rateType);
      } catch (Exception e) {
        log.error("Failed to fetch NY Fed rate {}: {}", rateType, e.getMessage());
      }
    }
  }

  @Retryable(includes = RestClientException.class, maxRetries = 2, delay = 1000, multiplier = 2)
  public List<NyFedRateResponse> fetchRates(String rateType) {
    NyFedRatesApiResponse response;
    try (var scope = tracer.span(IngestionTracingConfig.SPAN_NYFED_FETCH)) {
      String url = baseUrl + "/rates/all/" + rateType + "/latest.json";
      response = restClient
          .get()
          .uri(url)
          .retrieve()
          .body(NyFedRatesApiResponse.class);
    }

    if (response == null || response.refRates() == null) {
      return List.of();
    }

    return response
        .refRates()
        .stream()
        .filter(r -> r.percentRate() != null)
        .map(r -> new NyFedRateResponse(
            LocalDate
                .parse(r.effectiveDate(), NYFED_DATE_FMT)
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC), rateType, r.percentRate(), "NY_FED"))
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

  record NyFedRatesApiResponse(List<NyFedRateRaw> refRates) {}

  record NyFedRateRaw(String effectiveDate, Double percentRate) {}
}
