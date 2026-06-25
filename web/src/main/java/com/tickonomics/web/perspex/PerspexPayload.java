package com.tickonomics.web.perspex;

import com.tickonomics.persistence.entity.NewsEvent;
import com.tickonomics.persistence.entity.PredictionMarketQuote;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the analytics-worker request payload from persisted prediction-market quotes and news events.
 * Shared by {@link PerspexSignalGenerator} (scheduled broadcast) and {@link PerspexController}
 * (on-demand REST) so both send the identical shape.
 */
final class PerspexPayload {

  private PerspexPayload() {
  }

  static Map<String, Object> buildPayload(
      List<PredictionMarketQuote> quotes,
      List<NewsEvent> events,
      double divergenceThreshold,
      double minLiquidity,
      double minVolume) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("quotes", quotes.stream().map(PerspexPayload::quoteMap).toList());
    payload.put("events", events.stream().map(PerspexPayload::eventMap).toList());
    payload.put("divergence_threshold", divergenceThreshold);
    payload.put("min_liquidity", minLiquidity);
    payload.put("min_volume", minVolume);
    return payload;
  }

  private static Map<String, Object> quoteMap(PredictionMarketQuote quote) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("market_id", quote.marketId());
    map.put("question", quote.question());
    map.put("outcome_yes_price", quote.outcomeYesPrice());
    map.put("volume", quote.volume());
    map.put("liquidity", quote.liquidity());
    map.put("time", quote.time().toString());
    return map;
  }

  private static Map<String, Object> eventMap(NewsEvent event) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("event_id", event.eventId());
    map.put("headline", event.headline());
    map.put("avg_tone", event.avgTone());
    map.put("time", event.time().toString());
    return map;
  }
}
