package com.tickonomics.web.perspex;

import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import com.tickonomics.persistence.entity.NewsEvent;
import com.tickonomics.persistence.entity.PredictionMarketQuote;
import com.tickonomics.persistence.repository.NewsEventRepository;
import com.tickonomics.persistence.repository.PredictionMarketQuoteRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the perspective-mismatch feature (ADR-037). Returns the current on-demand
 * mismatches by asking the analytics worker to evaluate recent quotes and events. The real-time path
 * is the WebSocket broadcast from {@link PerspexSignalGenerator}; this endpoint is for ad-hoc inspection.
 */
@RestController
@RequestMapping("/api/v1/perspex")
public class PerspexController {

  private final PredictionMarketQuoteRepository quoteRepository;
  private final NewsEventRepository newsRepository;
  private final AnalyticsWorkerClient workerClient;

  @Value("${monitor.perspex.lookback-minutes:240}")
  private long lookbackMinutes;

  @Value("${monitor.perspex.divergence-threshold:0.25}")
  private double divergenceThreshold;

  @Value("${monitor.perspex.min-liquidity:1000.0}")
  private double minLiquidity;

  @Value("${monitor.perspex.min-volume:500.0}")
  private double minVolume;

  @Value("${monitor.perspex.max-quotes:200}")
  private int maxQuotes;

  @Value("${monitor.perspex.max-events:200}")
  private int maxEvents;

  @Value("${monitor.perspex.worker-function:api/v1/perspex/analyze}")
  private String workerFunction;

  public PerspexController(
      PredictionMarketQuoteRepository quoteRepository,
      NewsEventRepository newsRepository,
      AnalyticsWorkerClient workerClient) {
    this.quoteRepository = quoteRepository;
    this.newsRepository = newsRepository;
    this.workerClient = workerClient;
  }

  @GetMapping("/mismatches")
  public ResponseEntity<Object> mismatches() {
    Instant since = Instant.now().minus(lookbackMinutes, ChronoUnit.MINUTES);
    List<PredictionMarketQuote> quotes = quoteRepository.findRecent(since, maxQuotes);
    List<NewsEvent> events = newsRepository.findRecent(since, maxEvents);
    Map<String, Object> payload =
        PerspexPayload.buildPayload(quotes, events, divergenceThreshold, minLiquidity, minVolume);
    Map<String, Object> response = workerClient.sendAnalysisRequest(workerFunction, payload);
    if (response == null || response.containsKey("error")) {
      ProblemDetail problem = ProblemDetail.forStatusAndDetail(
          HttpStatus.SERVICE_UNAVAILABLE,
          response == null ? "analytics worker unavailable" : String.valueOf(response.get("error")));
      problem.setTitle("Worker unavailable");
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
    return ResponseEntity.ok(response);
  }
}
