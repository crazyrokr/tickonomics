package com.tickonomics.computation.backtest;

import com.tickonomics.persistence.entity.TickData;
import com.tickonomics.persistence.repository.TickDataRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HistoricalDataReplay {

  private final TickDataRepository tickDataRepository;

  public HistoricalDataReplay(TickDataRepository tickDataRepository) {
    this.tickDataRepository = tickDataRepository;
  }

  public List<TickData> replay(String symbol, Instant from, Instant to) {
    return tickDataRepository.findBySymbolAndTimeBetween(symbol, from, to);
  }

  public Map<String, List<TickData>> replay(List<String> symbols, Instant from, Instant to) {
    Map<String, List<TickData>> result = new LinkedHashMap<>();
    for (String symbol : symbols) {
      result.put(symbol, replay(symbol, from, to));
    }
    return result;
  }

  public List<Double> computeDailyReturns(List<TickData> ticks) {
    if (ticks == null || ticks.size() < 2) {
      return List.of();
    }

    List<TickData> dailyLast = extractDailyLastPrices(ticks);
    if (dailyLast.size() < 2) {
      return List.of();
    }

    List<Double> returns = new ArrayList<>();
    for (int i = 1; i < dailyLast.size(); i++) {
      double prev = dailyLast.get(i - 1).price().doubleValue();
      double curr = dailyLast.get(i).price().doubleValue();
      if (prev != 0) {
        returns.add((curr - prev) / prev);
      }
    }
    return returns;
  }

  private List<TickData> extractDailyLastPrices(List<TickData> ticks) {
    Map<String, TickData> dailyLast = new LinkedHashMap<>();
    for (TickData tick : ticks) {
      String dayKey = tick.time().toString().substring(0, 10);
      TickData existing = dailyLast.get(dayKey);
      if (existing == null || tick.time().isAfter(existing.time())) {
        dailyLast.put(dayKey, tick);
      }
    }
    return new ArrayList<>(dailyLast.values());
  }
}
