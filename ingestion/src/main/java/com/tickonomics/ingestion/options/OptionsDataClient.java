package com.tickonomics.ingestion.options;

import com.tickonomics.ingestion.writer.TimescaleDbWriter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "monitor.ingestion.options.enabled", havingValue = "true")
public class OptionsDataClient {

    private static final Logger log = LoggerFactory.getLogger(OptionsDataClient.class);

    private final RestClient restClient;
    private final TimescaleDbWriter writer;

    @Value("${monitor.ingestion.options.base-url:https://api.polygon.io}")
    private String baseUrl;

    @Value("${monitor.ingestion.options.api-key:}")
    private String apiKey;

    @Value("${monitor.ingestion.options.symbols:SPY}")
    private List<String> symbols;

    public OptionsDataClient(RestClient.Builder restClientBuilder, TimescaleDbWriter writer) {
        this.restClient = restClientBuilder.build();
        this.writer = writer;
    }

    @Scheduled(fixedDelayString = "${monitor.ingestion.options.poll-interval-ms:300000}")
    @Retry(name = "optionsApi")
    public void pollOptionsSnapshots() {
        for (String symbol : symbols) {
            try {
                var snapshots = fetchOptionsSnapshot(symbol);
                log.info("Fetched {} option snapshots for {}", snapshots.size(), symbol);
            } catch (Exception e) {
                log.error("Failed to fetch options for {}: {}", symbol, e.getMessage());
            }
        }
    }

    public List<OptionsChainSnapshot> fetchOptionsSnapshot(String symbol) {
        String url = baseUrl + "/v3/snapshot/options/{symbol}?apiKey={apiKey}";
        var response = restClient.get()
                .uri(url, symbol, apiKey)
                .retrieve()
                .body(PolygonOptionsResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(r -> r.bid() > 0 && r.ask() > 0)
                .map(this::toSnapshot)
                .toList();
    }

    private OptionsChainSnapshot toSnapshot(PolygonOptionResult r) {
        return new OptionsChainSnapshot(
                Instant.now(), r.underlying(), r.strike(), r.expiry(),
                r.optionType(), r.bid(), r.ask(), r.lastPrice(),
                r.impliedVol(), r.delta(), r.gamma(), r.theta(),
                r.vega(), r.rho(), r.openInterest(), r.underlyingPrice());
    }

    record PolygonOptionsResponse(List<PolygonOptionResult> results) {}

    record PolygonOptionResult(
            String underlying, java.math.BigDecimal strike, java.time.LocalDate expiry,
            String optionType, double bid, double ask, double lastPrice,
            double impliedVol, double delta, double gamma, double theta,
            double vega, double rho, long openInterest, double underlyingPrice) {}
}
