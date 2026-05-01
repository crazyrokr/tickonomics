package com.tickonomics.ingestion.nyfed;

import com.tickonomics.cdm.adapter.NyFedCdmAdapter;
import com.tickonomics.cdm.adapter.raw.NyFedRateResponse;
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
public class NyFedClient {

  private static final Logger log = LoggerFactory.getLogger(NyFedClient.class);
  private static final DateTimeFormatter NYFED_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final List<String> RATE_TYPES = List.of("sofr", "tgcr", "bgcr");

  private final RestClient restClient;
  private final RateSnapshotRepository rateRepository;
  private final NyFedCdmAdapter cdmAdapter;

  @Value("${nyfed.base-url:https://markets.newyorkfed.org/api}")
  private String baseUrl;

  public NyFedClient(
      RestClient.Builder restClientBuilder,
      RateSnapshotRepository rateRepository,
      NyFedCdmAdapter cdmAdapter) {
    this.restClient = restClientBuilder.build();
    this.rateRepository = rateRepository;
    this.cdmAdapter = cdmAdapter;
  }

  @Scheduled(fixedDelayString = "${nyfed.poll-interval-ms:300000}")
  public void pollAllRates() {
    for (String rateType : RATE_TYPES) {
      try {
        var responses = fetchRates(rateType);
        for (var resp : responses) {
          var cdm = cdmAdapter.toCdm(resp);
          rateRepository.save(toEntity(cdm));
        }
        log.info("Fetched {} observations for NY Fed rate {}", responses.size(), rateType);
      } catch (Exception e) {
        log.error("Failed to fetch NY Fed rate {}: {}", rateType, e.getMessage());
      }
    }
  }

  public List<NyFedRateResponse> fetchRates(String rateType) {
    String url = baseUrl + "/rates/all/" + rateType + "/latest.json";
    var response = restClient
        .get()
        .uri(url)
        .retrieve()
        .body(NyFedRatesApiResponse.class);

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
        cdm.source());
  }

  record NyFedRatesApiResponse(List<NyFedRateRaw> refRates) {}

  record NyFedRateRaw(String effectiveDate, Double percentRate) {}
}
