package com.tickonomics.computation.demo;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.kpi.ReturnGapCalculator;
import com.tickonomics.persistence.entity.SignalLog;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.SignalLogRepository;
import com.tickonomics.persistence.repository.SignalQualityReportRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignalQualityAnalyzerTest {

  @Mock
  private SignalLogRepository signalLogRepository;
  @Mock
  private VirtualPortfolioTradeRepository tradeRepository;
  @Mock
  private SignalQualityReportRepository reportRepository;
  @Mock
  private MarketPriceLookup priceLookup;

  private SignalQualityAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    DemoConfig config = DemoConfig.core(
        true, new BigDecimal("100000.00"), 5.0, 5.0, 10.0, true, true, true, 10_000_000.0);
    analyzer = new SignalQualityAnalyzer(signalLogRepository, tradeRepository, reportRepository,
        priceLookup, new ReturnGapCalculator(), config);
  }

  private List<VirtualPortfolioTrade> trades(double... pnls) {
    return IntStream.range(0, pnls.length)
        .mapToObj(i -> new VirtualPortfolioTrade((long) i + 1, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("500.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(pnls[i]), (long) i + 1, null, "PAPER"))
        .toList();
  }

  @Nested
  class ComputePortfolioPnl {

    @Test
    void givenMixedPnls_whenComputePortfolioPnl_thenSum() {
      assertEquals(50.0, analyzer.computePortfolioPnl(trades(100.0, -50.0, 20.0, -20.0)));
    }

    @Test
    void givenNullRealizedPnls_whenComputePortfolioPnl_thenSkipped() {
      List<VirtualPortfolioTrade> trades = List.of(
          new VirtualPortfolioTrade(1L, Instant.now(), "SPY", "SELL", new BigDecimal("10.0"), new BigDecimal("500.0"), BigDecimal.ZERO, BigDecimal.ZERO, null, 1L, null, "PAPER"));
      assertEquals(0.0, analyzer.computePortfolioPnl(trades));
    }
  }

  @Nested
  class ComputeSharpe {

    @Test
    void givenNoTrades_whenComputeSharpe_thenZero() {
      assertEquals(0.0, analyzer.computeSharpe(List.of()));
    }

    @Test
    void givenEqualReturns_whenComputeSharpe_thenZeroVariance() {
      assertEquals(0.0, analyzer.computeSharpe(trades(10.0, 10.0, 10.0)));
    }
  }

  @Nested
  class ComputeWinRate {

    @Test
    void givenHalfWinners_whenComputeWinRate_thenHalf() {
      assertEquals(0.5, analyzer.computeWinRate(trades(10.0, -10.0)), 0.0001);
    }

    @Test
    void givenNoTrades_whenComputeWinRate_thenZero() {
      assertEquals(0.0, analyzer.computeWinRate(List.of()));
    }
  }

  @Nested
  class ComputeMaxDrawdown {

    @Test
    void givenMonotonicDecline_whenComputeMaxDrawdown_thenPeakToTrough() {
      assertEquals(70.0, analyzer.computeMaxDrawdown(trades(10.0, -20.0, -30.0, -20.0)));
    }

    @Test
    void givenNoTrades_whenComputeMaxDrawdown_thenZero() {
      assertEquals(0.0, analyzer.computeMaxDrawdown(List.of()));
    }
  }

  @Nested
  class ComputeHitStats {

    private final LocalDate signalDate = LocalDate.of(2026, 5, 1);
    private final LocalDate reportDate = LocalDate.of(2026, 6, 13);

    private SignalLog signal(String symbol, String direction, String status) {
      Instant createdAt = signalDate.atStartOfDay(ZoneOffset.UTC).toInstant();
      return new SignalLog(createdAt, symbol, direction, status, 85.0, 1.5, 0.02, 0.0, "{}");
    }

    @Test
    void givenMixedSignals_whenComputeHitStats_thenRatesAndAttributionCorrect() {
      Map<String, Double> prices = new HashMap<>();
      prices.put("SPY|2026-05-01", 100.0);
      prices.put("SPY|2026-05-02", 101.0);
      prices.put("SPY|2026-05-06", 105.0);
      prices.put("SPY|2026-05-11", 104.0);
      prices.put("SPY|2026-05-21", 110.0);
      prices.put("QQQ|2026-05-01", 100.0);
      prices.put("QQQ|2026-05-02", 99.0);
      prices.put("QQQ|2026-05-06", 95.0);
      prices.put("QQQ|2026-05-11", 96.0);
      prices.put("QQQ|2026-05-21", 90.0);
      prices.put("AAPL|2026-05-01", 100.0);
      prices.put("AAPL|2026-05-02", 100.0);
      prices.put("AAPL|2026-05-06", 98.0);
      prices.put("AAPL|2026-05-11", 97.0);
      prices.put("AAPL|2026-05-21", 96.0);
      when(priceLookup.closingPrice(any(), any())).thenAnswer(inv ->
          Optional.ofNullable(prices.get(inv.getArgument(0) + "|" + inv.getArgument(1))));

      List<SignalLog> signals = List.of(
          signal("SPY", "BUY", "ACTIONABLE"),
          signal("QQQ", "SELL", "ACTIONABLE"),
          signal("AAPL", "BUY", "SPECULATIVE_STALE_MACRO"));

      SignalQualityAnalyzer.HitStats stats = analyzer.computeHitStats(signals, reportDate);

      assertEquals(2.0 / 3.0, stats.rate1d(), 0.0001);
      assertEquals(2.0 / 3.0, stats.rate5d(), 0.0001);
      assertEquals(2, stats.validCount());
      assertEquals(1, stats.degradedCount());
      assertEquals(1.0 / 3.0, stats.falsePositiveRate(), 0.0001);
      assertEquals(1, stats.dataAnomalyMisses());
      assertEquals(0, stats.logicBoundsMisses());
    }

    @Test
    void givenNoPriceData_whenComputeHitStats_thenAllRatesZero() {
      when(priceLookup.closingPrice(any(), any())).thenReturn(Optional.empty());

      SignalQualityAnalyzer.HitStats stats = analyzer.computeHitStats(
          List.of(signal("SPY", "BUY", "ACTIONABLE")), reportDate);

      assertEquals(0.0, stats.rate5d());
      assertEquals(0, stats.samples5d());
    }

    @Test
    void givenWindowNotClosed_whenComputeHitStats_thenHorizonSkipped() {
      LocalDate recentDate = LocalDate.of(2026, 6, 10);
      SignalLog recent = new SignalLog(
          recentDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
          "SPY", "BUY", "ACTIONABLE", 85.0, 1.5, 0.02, 0.0, "{}");
      when(priceLookup.closingPrice(eq("SPY"), eq(recentDate))).thenReturn(Optional.of(100.0));

      SignalQualityAnalyzer.HitStats stats = analyzer.computeHitStats(List.of(recent), reportDate);

      assertEquals(0, stats.samples5d());
    }
  }

  @Nested
  class ComputeVsSpyReturn {

    @Test
    void givenRisingSpy_whenComputeVsSpyReturn_thenPositive() {
      when(priceLookup.closingPrices(any(), any(), any()))
          .thenReturn(List.of(100.0, 102.0, 105.0));

      double ret = analyzer.computeVsSpyReturn(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

      assertEquals(0.05, ret, 0.0001);
    }

    @Test
    void givenSingleClose_whenComputeVsSpyReturn_thenZero() {
      when(priceLookup.closingPrices(any(), any(), any())).thenReturn(List.of(100.0));

      assertEquals(0.0, analyzer.computeVsSpyReturn(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void givenNullStartDate_whenComputeVsSpyReturn_thenZero() {
      assertEquals(0.0, analyzer.computeVsSpyReturn(null, LocalDate.of(2026, 6, 1)));
    }
  }

  @Nested
  class GenerateReport {

    @Test
    void givenTradesAndSignals_whenGenerateReport_thenReportSavedWithEnrichedMetrics() {
      LocalDate reportDate = LocalDate.of(2026, 6, 13);
      when(signalLogRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
      when(tradeRepository.countByTradeType("PAPER")).thenReturn(0);
      when(tradeRepository.findLatest(0, 0)).thenReturn(List.of());

      SignalQualityAnalyzer.SignalQualityReportData data = analyzer.generateReport(reportDate);

      assertEquals(reportDate, data.reportDate());
      assertEquals(0.0, data.hitStats().rate5d());
      assertEquals(0.0, data.vsSpyReturn());
      assertTrue(data.verificationProgress().contains("hitRateByHorizon"));
      assertTrue(data.verificationProgress().contains("mistakeAttribution"));
      assertTrue(data.verificationProgress().contains("returnGap"));
    }
  }
}
