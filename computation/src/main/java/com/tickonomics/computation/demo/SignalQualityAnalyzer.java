package com.tickonomics.computation.demo;

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

  private final SignalLogRepository signalLogRepository;
  private final VirtualPortfolioTradeRepository tradeRepository;
  private final SignalQualityReportRepository reportRepository;

  public SignalQualityAnalyzer(
      SignalLogRepository signalLogRepository,
      VirtualPortfolioTradeRepository tradeRepository,
      SignalQualityReportRepository reportRepository) {
    this.signalLogRepository = signalLogRepository;
    this.tradeRepository = tradeRepository;
    this.reportRepository = reportRepository;
  }

  public SignalQualityReportData generateReport(LocalDate reportDate) {
    Instant startOfDay = reportDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endOfDay = reportDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    List<SignalLog> signals = signalLogRepository.findByCreatedAtBetween(startOfDay, endOfDay);
    int totalSignals = signals.size();
    int actionableSignals = (int) signals.stream()
        .filter(s -> "ACTIONABLE".equals(s.status()))
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

    String verificationProgress = buildVerificationProgress(
        startDate, daysElapsed, totalTrades, winRate, portfolioSharpe, maxDrawdown);

    reportRepository.save(
        reportDate, totalSignals, actionableSignals,
        null, null, null, null,
        null, null, portfolioPnl, portfolioSharpe, null, verificationProgress);

    return new SignalQualityReportData(
        reportDate, totalSignals, actionableSignals,
        portfolioPnl, portfolioSharpe, winRate, verificationProgress);
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

  String buildVerificationProgress(LocalDate startDate, int daysElapsed,
      int totalTrades, double winRate, double sharpe, double maxDrawdown) {

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
          "allCriteriaMet": %s
        }""".formatted(
        startDate != null ? startDate : "",
        daysElapsed,
        TARGET_OPERATION_DAYS, daysElapsed, daysMet,
        TARGET_MIN_TRADES, totalTrades, tradesMet,
        TARGET_HIT_RATE, winRate, hitRateMet,
        TARGET_SHARPE, sharpe, sharpeMet,
        TARGET_MAX_DRAWDOWN * 100_000, maxDrawdown, drawdownMet,
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

  public record SignalQualityReportData(
      LocalDate reportDate,
      int totalSignals,
      int actionableSignals,
      double portfolioPnl,
      double portfolioSharpe,
      double winRate,
      String verificationProgress) {}
}
