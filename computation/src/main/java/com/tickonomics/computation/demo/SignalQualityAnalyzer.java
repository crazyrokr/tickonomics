package com.tickonomics.computation.demo;

import com.tickonomics.computation.kpi.ReturnGapCalculator;
import com.tickonomics.persistence.entity.SignalLog;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.SignalQualityReportRepository;
import com.tickonomics.persistence.repository.SignalLogRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SignalQualityAnalyzer {

  private static final double TARGET_HIT_RATE = 0.55;
  private static final double TARGET_SHARPE = 0.50;
  private static final double TARGET_MAX_DRAWDOWN = 0.20;
  private static final int TARGET_OPERATION_DAYS = 90;
  private static final int TARGET_MIN_TRADES = 50;
  private static final double DEFAULT_COST_BPS = 5.0;
  private static final int HIT_HORIZON_DAYS = 5;
  private static final int[] HIT_HORIZONS = {1, 5, 10, 20};
  private static final int LOOKBACK_DAYS = 400;
  private static final String SPY_BENCHMARK = "SPY";

  private final SignalLogRepository signalLogRepository;
  private final VirtualPortfolioTradeRepository tradeRepository;
  private final SignalQualityReportRepository reportRepository;
  private final MarketPriceLookup priceLookup;
  private final ReturnGapCalculator returnGapCalculator;
  private final DemoConfig config;

  public SignalQualityAnalyzer(
      SignalLogRepository signalLogRepository,
      VirtualPortfolioTradeRepository tradeRepository,
      SignalQualityReportRepository reportRepository,
      MarketPriceLookup priceLookup,
      ReturnGapCalculator returnGapCalculator,
      DemoConfig config) {
    this.signalLogRepository = signalLogRepository;
    this.tradeRepository = tradeRepository;
    this.reportRepository = reportRepository;
    this.priceLookup = priceLookup;
    this.returnGapCalculator = returnGapCalculator;
    this.config = config;
  }

  public SignalQualityReportData generateReport(LocalDate reportDate) {
    Instant startOfDay = reportDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endOfDay = reportDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    List<SignalLog> windowSignals = signalLogRepository.findByCreatedAtBetween(
        reportDate.minusDays(LOOKBACK_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant(), endOfDay);

    int totalSignals = (int) windowSignals.stream()
        .filter(s -> !s.createdAt().isBefore(startOfDay) && s.createdAt().isBefore(endOfDay))
        .count();
    int actionableSignals = (int) windowSignals.stream()
        .filter(s -> "ACTIONABLE".equals(s.status()))
        .filter(s -> !s.createdAt().isBefore(startOfDay) && s.createdAt().isBefore(endOfDay))
        .count();

    int totalTrades = tradeRepository.countByTradeType("PAPER");
    List<VirtualPortfolioTrade> allTrades = tradeRepository.findLatest(totalTrades, 0);

    double portfolioPnl = computePortfolioPnl(allTrades);
    double portfolioSharpe = computeSharpe(allTrades);
    double winRate = computeWinRate(allTrades);
    double maxDrawdown = computeMaxDrawdown(allTrades);

    LocalDate startDate = findStartDate(allTrades);
    int daysElapsed = startDate != null
        ? (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, reportDate) + 1
        : 0;

    HitStats hitStats = computeHitStats(windowSignals, reportDate);
    double vsSpyReturn = computeVsSpyReturn(startDate, reportDate);
    double portfolioReturn = config.virtualBalance() > 0
        ? portfolioPnl / config.virtualBalance() : 0.0;
    ReturnGapCalculator.ReturnGapResult returnGap =
        returnGapCalculator.calculate(portfolioReturn, vsSpyReturn);

    String verificationProgress = buildVerificationProgress(
        startDate, daysElapsed, totalTrades, winRate, portfolioSharpe, maxDrawdown,
        hitStats, returnGap);

    reportRepository.save(
        reportDate, totalSignals, actionableSignals,
        hitStats.rate1d, hitStats.rate5d, hitStats.rate10d, hitStats.rate20d,
        hitStats.falsePositiveRate, hitStats.avgReturnPerSignal, portfolioPnl,
        portfolioSharpe, vsSpyReturn, verificationProgress);

    return new SignalQualityReportData(
        reportDate, totalSignals, actionableSignals,
        portfolioPnl, portfolioSharpe, winRate, hitStats, vsSpyReturn, returnGap,
        verificationProgress);
  }

  double computePortfolioPnl(List<VirtualPortfolioTrade> trades) {
    return trades.stream()
        .filter(t -> t.realizedPnl() != null)
        .mapToDouble(VirtualPortfolioTrade::realizedPnl)
        .sum();
  }

  double computeSharpe(List<VirtualPortfolioTrade> trades) {
    List<Double> returns = trades.stream()
        .filter(t -> t.realizedPnl() != null)
        .mapToDouble(VirtualPortfolioTrade::realizedPnl)
        .boxed()
        .toList();

    if (returns.isEmpty()) {
      return 0.0;
    }

    double mean = returns.stream().mapToDouble(d -> d).average().orElse(0.0);
    double variance = returns.stream()
        .mapToDouble(d -> Math.pow(d - mean, 2))
        .average()
        .orElse(0.0);

    return Math.sqrt(variance) > 0 ? mean / Math.sqrt(variance) : 0.0;
  }

  double computeWinRate(List<VirtualPortfolioTrade> trades) {
    List<Double> pnls = trades.stream()
        .filter(t -> t.realizedPnl() != null)
        .mapToDouble(VirtualPortfolioTrade::realizedPnl)
        .boxed()
        .toList();

    if (pnls.isEmpty()) {
      return 0.0;
    }

    long wins = pnls.stream().filter(p -> p > 0).count();
    return (double) wins / pnls.size();
  }

  double computeMaxDrawdown(List<VirtualPortfolioTrade> trades) {
    List<Double> pnls = trades.stream()
        .filter(t -> t.realizedPnl() != null)
        .mapToDouble(VirtualPortfolioTrade::realizedPnl)
        .boxed()
        .toList();

    if (pnls.isEmpty()) {
      return 0.0;
    }

    double cumPnl = 0.0;
    double peak = 0.0;
    double maxDd = 0.0;

    for (Double pnl : pnls) {
      cumPnl += pnl;
      peak = Math.max(peak, cumPnl);
      maxDd = Math.max(maxDd, peak - cumPnl);
    }

    return maxDd;
  }

  /**
   * Hit-rate-by-horizon, false-positive rate, average signed return, and mistake attribution over
   * the demo signal window. A signal "hits" at horizon N when the close N calendar days later moved
   * in the signalled direction. A false positive is an adverse move beyond the signal's cost
   * threshold (5d horizon). Misses are attributed to {@code DATA_ANOMALY} (non-actionable / degraded
   * signal) or {@code LOGIC_BOUNDS} (actionable signal that missed).
   */
  HitStats computeHitStats(List<SignalLog> signals, LocalDate reportDate) {
    int[] hits = new int[HIT_HORIZONS.length];
    int[] samples = new int[HIT_HORIZONS.length];
    int validCount = 0;
    int degradedCount = 0;
    int fprMisses = 0;
    int fprSamples = 0;
    int dataAnomalyMisses = 0;
    int logicBoundsMisses = 0;
    double returnsSum = 0.0;
    int returnsCount = 0;

    for (SignalLog signal : signals) {
      boolean actionable = "ACTIONABLE".equals(signal.status());
      if (actionable) {
        validCount++;
      } else {
        degradedCount++;
      }
      LocalDate signalDate = signal.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
      Double entry = priceLookup.closingPrice(signal.symbol(), signalDate).orElse(null);
      if (entry == null) {
        continue;
      }
      for (int i = 0; i < HIT_HORIZONS.length; i++) {
        LocalDate evalDate = signalDate.plusDays(HIT_HORIZONS[i]);
        if (evalDate.isAfter(reportDate)) {
          continue;
        }
        Double exit = priceLookup.closingPrice(signal.symbol(), evalDate).orElse(null);
        if (exit == null) {
          continue;
        }
        double pctChange = (exit - entry) / entry;
        double signedReturn = "SELL".equals(signal.direction()) ? -pctChange : pctChange;
        samples[i]++;
        if (signedReturn > 0.0) {
          hits[i]++;
        }
        if (HIT_HORIZONS[i] == HIT_HORIZON_DAYS) {
          returnsSum += signedReturn;
          returnsCount++;
          double costFraction = (signal.estimatedCost() > 0 ? signal.estimatedCost() : DEFAULT_COST_BPS) / 10_000.0;
          if (signedReturn < -costFraction) {
            fprMisses++;
          }
          fprSamples++;
          if (signedReturn <= 0.0) {
            if (actionable) {
              logicBoundsMisses++;
            } else {
              dataAnomalyMisses++;
            }
          }
        }
      }
    }

    double[] rates = new double[HIT_HORIZONS.length];
    for (int i = 0; i < HIT_HORIZONS.length; i++) {
      rates[i] = samples[i] > 0 ? (double) hits[i] / samples[i] : 0.0;
    }
    double falsePositiveRate = fprSamples > 0 ? (double) fprMisses / fprSamples : 0.0;
    double avgReturnPerSignal = returnsCount > 0 ? returnsSum / returnsCount : 0.0;

    return new HitStats(
        rates[0], rates[1], rates[2], rates[3],
        falsePositiveRate, avgReturnPerSignal,
        validCount, degradedCount, samples[1],
        dataAnomalyMisses, logicBoundsMisses);
  }

  double computeVsSpyReturn(LocalDate startDate, LocalDate reportDate) {
    if (startDate == null) {
      return 0.0;
    }
    List<Double> spyCloses = priceLookup.closingPrices(
        SPY_BENCHMARK, startDate, reportDate.plusDays(1));
    if (spyCloses.size() < 2) {
      return 0.0;
    }
    double first = spyCloses.getFirst();
    double last = spyCloses.getLast();
    return first > 0 ? (last - first) / first : 0.0;
  }

  String buildVerificationProgress(LocalDate startDate, int daysElapsed,
      int totalTrades, double winRate, double sharpe, double maxDrawdown,
      HitStats hitStats, ReturnGapCalculator.ReturnGapResult returnGap) {

    boolean daysMet = daysElapsed >= TARGET_OPERATION_DAYS;
    boolean tradesMet = totalTrades >= TARGET_MIN_TRADES;
    boolean hitRateMet = winRate >= TARGET_HIT_RATE;
    boolean sharpeMet = sharpe >= TARGET_SHARPE;
    boolean drawdownMet = maxDrawdown <= TARGET_MAX_DRAWDOWN * 100_000;
    boolean allMet = daysMet && tradesMet && hitRateMet && sharpeMet && drawdownMet;

    return """
        {
          "startDate": "%s",
          "daysElapsed": %d,
          "criteria": {
            "minOperationDays": {"target": %d, "current": %d, "met": %s},
            "minTrades": {"target": %d, "current": %d, "met": %s},
            "hitRate": {"target": %.2f, "current": %.2f, "met": %s},
            "sharpeRatio": {"target": %.2f, "current": %.2f, "met": %s},
            "maxDrawdown": {"target": %.2f, "current": %.2f, "met": %s}
          },
          "hitRateByHorizon": {"1d": {"rate": %.4f, "samples": %d}, "5d": {"rate": %.4f}, "10d": {"rate": %.4f}, "20d": {"rate": %.4f}},
          "signalBreakdown": {"valid": %d, "degraded": %d},
          "returnGap": {"value": %.6f, "interpretation": "%s", "vsSpy": %.6f},
          "mistakeAttribution": {"dataAnomaly": %d, "logicBounds": %d},
          "allCriteriaMet": %s
        }""".formatted(
        startDate != null ? startDate : "",
        daysElapsed,
        TARGET_OPERATION_DAYS, daysElapsed, daysMet,
        TARGET_MIN_TRADES, totalTrades, tradesMet,
        TARGET_HIT_RATE, winRate, hitRateMet,
        TARGET_SHARPE, sharpe, sharpeMet,
        TARGET_MAX_DRAWDOWN * 100_000, maxDrawdown, drawdownMet,
        hitStats.rate1d, hitStats.samples5d,
        hitStats.rate5d, hitStats.rate10d, hitStats.rate20d,
        hitStats.validCount, hitStats.degradedCount,
        returnGap.returnGap(), returnGap.interpretation(), returnGap.holdingsReturn(),
        hitStats.dataAnomalyMisses, hitStats.logicBoundsMisses,
        allMet);
  }

  private LocalDate findStartDate(List<VirtualPortfolioTrade> trades) {
    return trades.stream()
        .map(t -> t.executedAt().atZone(ZoneOffset.UTC).toLocalDate())
        .min(LocalDate::compareTo)
        .orElse(null);
  }

  public Optional<Map<String, Object>> findLatestReport() {
    return reportRepository.findLatest();
  }

  public record HitStats(
      double rate1d,
      double rate5d,
      double rate10d,
      double rate20d,
      double falsePositiveRate,
      double avgReturnPerSignal,
      int validCount,
      int degradedCount,
      int samples5d,
      int dataAnomalyMisses,
      int logicBoundsMisses) {}

  public record SignalQualityReportData(
      LocalDate reportDate,
      int totalSignals,
      int actionableSignals,
      double portfolioPnl,
      double portfolioSharpe,
      double winRate,
      HitStats hitStats,
      double vsSpyReturn,
      ReturnGapCalculator.ReturnGapResult returnGap,
      String verificationProgress) {}
}
