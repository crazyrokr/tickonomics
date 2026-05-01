package com.tickonomics.ingestion.fred;

import com.tickonomics.cdm.adapter.FredCdmAdapter;
import com.tickonomics.cdm.adapter.raw.FredObservation;
import com.tickonomics.cdm.model.CdmRateSnapshot;
import com.tickonomics.persistence.entity.RateSnapshot;
import com.tickonomics.persistence.repository.RateSnapshotRepository;
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
public class FredClient {

  private static final Logger log = LoggerFactory.getLogger(FredClient.class);
  private static final DateTimeFormatter FRED_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final List<String> SERIES_IDS = List.of("EFFR", "RRPONTSYD", "WTREGEN", "WALCL", "IORB");

  private final RestClient restClient;
  private final RateSnapshotRepository rateRepository;
  private final FredCdmAdapter cdmAdapter;

  @Value("${fred.api-key:}")
  private String apiKey;

  @Value("${fred.base-url:https://api.stlouisfed.org/fred}")
  private String baseUrl;

  public FredClient(
      RestClient.Builder restClientBuilder,
      RateSnapshotRepository rateRepository,
      FredCdmAdapter cdmAdapter) {
    this.restClient = restClientBuilder.build();
    this.rateRepository = rateRepository;
    this.cdmAdapter = cdmAdapter;
  }

  @Scheduled(fixedDelayString = "${fred.poll-interval-ms:300000}")
  public void pollAllSeries() {
    for (String seriesId : SERIES_IDS) {
      try {
        var observations = fetchSeries(seriesId);
        for (var obs : observations) {
          var cdm = cdmAdapter.toCdm(obs);
          rateRepository.save(toEntity(cdm));
        }
        log.info("Fetched {} observations for series {}", observations.size(), seriesId);
      } catch (Exception e) {
        log.error("Failed to fetch FRED series {}: {}", seriesId, e.getMessage());
      }
    }
  }

  public List<FredObservation> fetchSeries(String seriesId) {
    String url =
        baseUrl + "/series/observations?series_id={seriesId}&api_key={apiKey}&file_type=json&sort_order=desc&limit=10";
    var response = restClient
        .get()
        .uri(url, seriesId, apiKey)
        .retrieve()
        .body(FredSeriesResponse.class);

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
        cdm.source());
  }

  record FredSeriesResponse(List<FredObservationRaw> observations) {}

  record FredObservationRaw(String date, String value) {}
}
