package com.tickonomics.web.perspex;

import com.tickonomics.computation.audit.CodingRule;
import com.tickonomics.computation.audit.IntersubjectiveAuditService;
import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import com.tickonomics.persistence.entity.NewsEvent;
import com.tickonomics.persistence.entity.PredictionMarketQuote;
import com.tickonomics.persistence.repository.NewsEventRepository;
import com.tickonomics.persistence.repository.PredictionMarketQuoteRepository;
import com.tickonomics.web.realtime.SignalNotification;
import com.tickonomics.web.realtime.SignalWebSocketHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled perspective-mismatch signal generator (ADR-037, Track A). Fetches recent prediction-market
 * quotes and OSINT news events, asks the Python worker for the deterministic divergence verdict, logs
 * the transformation to the IR-Score trust gate, and broadcasts an equity-keyed {@link SignalNotification}
 * for each actionable mismatch.
 *
 * <p>The deterministic check is necessary and authoritative (CodingRule IR 0.95, above the 0.9 gate).
 * The LLM agent corroborator (Track B) will add a second, lower-IR entry that an agent-only path could
 * never clear on its own. Disabled by default; enable via {@code monitor.perspex.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "monitor.perspex.enabled", havingValue = "true", matchIfMissing = false)
public class PerspexSignalGenerator {

  private static final Logger log = LoggerFactory.getLogger(PerspexSignalGenerator.class);
  private static final UUID STRATEGY_ID = UUID.fromString("e0d5b3a1-7f2c-4b1e-9a6d-0123456789ab");

  private final PredictionMarketQuoteRepository quoteRepository;
  private final NewsEventRepository newsRepository;
  private final AnalyticsWorkerClient workerClient;
  private final IntersubjectiveAuditService auditService;
  private final SignalWebSocketHandler signalHandler;

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

  public PerspexSignalGenerator(
      PredictionMarketQuoteRepository quoteRepository,
      NewsEventRepository newsRepository,
      AnalyticsWorkerClient workerClient,
      IntersubjectiveAuditService auditService,
      SignalWebSocketHandler signalHandler) {
    this.quoteRepository = quoteRepository;
    this.newsRepository = newsRepository;
    this.workerClient = workerClient;
    this.auditService = auditService;
    this.signalHandler = signalHandler;
  }

  @Scheduled(fixedDelayString = "${monitor.perspex.poll-interval-ms:600000}")
  public void generate() {
    try {
      Instant since = Instant.now().minus(lookbackMinutes, ChronoUnit.MINUTES);
      List<PredictionMarketQuote> quotes = quoteRepository.findRecent(since, maxQuotes);
      List<NewsEvent> events = newsRepository.findRecent(since, maxEvents);
      if (quotes.isEmpty() || events.isEmpty()) {
        return;
      }

      Map<String, Object> response = workerClient.sendAnalysisRequest(
          workerFunction, PerspexPayload.buildPayload(quotes, events, divergenceThreshold, minLiquidity, minVolume));
      if (response == null || response.containsKey("error")) {
        log.warn("Perspex worker call failed: {}", response == null ? "null response" : response.get("error"));
        return;
      }

      Object mismatches = response.get("mismatches");
      if (!(mismatches instanceof List<?> list)) {
        return;
      }
      int emitted = 0;
      for (Object item : list) {
        if (item instanceof Map<?, ?> mismatch && emitSignal(mismatch)) {
          emitted++;
        }
      }
      if (emitted > 0) {
        log.info("Perspex emitted {} perspective-mismatch signals", emitted);
      }
    } catch (Exception e) {
      log.error("Perspex signal generation failed: {}", e.getMessage());
    }
  }

  /**
   * Logs the deterministic transformation (IR 0.95), applies the trust gate, and broadcasts a signal
   * per resolved ticker. Returns true only if a signal cleared the gate and was broadcast.
   */
  boolean emitSignal(Map<?, ?> mismatch) {
    String marketId = String.valueOf(mismatch.get("market_id"));
    Object tickersObj = mismatch.get("tickers");
    if (!(tickersObj instanceof List<?> tickers) || tickers.isEmpty()) {
      return false;
    }
    String direction = "bullish".equals(mismatch.get("direction")) ? "LONG" : "SHORT";
    double divergence = number(mismatch.get("divergence"));
    double confidence = number(mismatch.get("confidence"));

    boolean emitted = false;
    for (Object tickerObj : tickers) {
      String ticker = String.valueOf(tickerObj);
      UUID dataPointId = UUID.randomUUID();
      auditService.logTransformation(
          dataPointId, CodingRule.PERSPECTIVE_MISMATCH_DETERMINISTIC, marketId + ":" + ticker, divergence);
      if (!auditService.isActionable(dataPointId)) {
        log.debug("Perspex signal gated for {} (IR below trust threshold)", ticker);
        continue;
      }
      signalHandler.broadcast(new SignalNotification(
          signalId(marketId, ticker), Instant.now(), ticker, direction, STRATEGY_ID, divergence, confidence));
      emitted = true;
    }
    return emitted;
  }

  private static UUID signalId(String marketId, String ticker) {
    return UUID.nameUUIDFromBytes(("perspex:" + marketId + ":" + ticker).getBytes(StandardCharsets.UTF_8));
  }

  private static double number(Object value) {
    return value instanceof Number n ? n.doubleValue() : 0.0;
  }
}
