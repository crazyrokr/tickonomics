package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickonomics.persistence.entity.SignalLog;
import com.tickonomics.persistence.entity.VirtualPortfolioTrade;
import com.tickonomics.persistence.repository.SignalLogRepository;
import com.tickonomics.persistence.repository.SignalQualityReportRepository;
import com.tickonomics.persistence.repository.VirtualPortfolioTradeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

  private SignalQualityAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer = new SignalQualityAnalyzer(signalLogRepository, tradeRepository, reportRepository);
  }

  @Nested
  class ComputePortfolioPnl {

    @Test
    void givenWinningTrades_whenComputePnl_thenPositiveSum() {
      VirtualPortfolioTrade t1 = new VirtualPortfolioTrade(
          1L, Instant.now(), "SPY", "SELL", 10.0, 500.0, 0, 0, 100.0, null, null, "PAPER");
      VirtualPortfolioTrade t2 = new VirtualPortfolioTrade(
          2L, Instant.now(), "AAPL", "BUY", 10.0, 180.0, 0, 0, 50.0, null, null, "PAPER");

      double pnl = analyzer.computePortfolioPnl(List.of(t1, t2));

      assertEquals(150.0, pnl, 0.001);
    }

    @Test
    void givenMixedTrades_whenComputePnl_thenNetResult() {
      VirtualPortfolioTrade win = new VirtualPortfolioTrade(
          1L, Instant.now(), "SPY", "SELL", 10.0, 500.0, 0, 0, 200.0, null, null, "PAPER");
      VirtualPortfolioTrade loss = new VirtualPortfolioTrade(
          2L, Instant.now(), "AAPL", "BUY", 10.0, 180.0, 0, 0, -100.0, null, null, "PAPER");

      double pnl = analyzer.computePortfolioPnl(List.of(win, loss));

      assertEquals(100.0, pnl, 0.001);
    }

    @Test
    void givenNoTrades_whenComputePnl_thenZero() {
      assertEquals(0.0, analyzer.computePortfolioPnl(List.of()));
    }
  }

  @Nested
  class ComputeSharpe {

    @Test
    void givenPositiveReturns_whenComputeSharpe_thenPositiveValue() {
      List<VirtualPortfolioTrade> trades = List.of(
          tradeWithPnl(100.0),
          tradeWithPnl(110.0),
          tradeWithPnl(90.0));

      double sharpe = analyzer.computeSharpe(trades);

      assertTrue(sharpe > 0);
    }

    @Test
    void givenZeroTrades_whenComputeSharpe_thenZero() {
      assertEquals(0.0, analyzer.computeSharpe(List.of()));
    }

    @Test
    void givenVaryingReturns_whenComputeSharpe_thenLowerThanStable() {
      List<VirtualPortfolioTrade> stable = List.of(
          tradeWithPnl(100.0), tradeWithPnl(110.0), tradeWithPnl(90.0));
      List<VirtualPortfolioTrade> varying = List.of(
          tradeWithPnl(300.0), tradeWithPnl(-200.0), tradeWithPnl(200.0));

      double stableSharpe = analyzer.computeSharpe(stable);
      double varyingSharpe = analyzer.computeSharpe(varying);

      assertTrue(stableSharpe > varyingSharpe);
    }
  }

  @Nested
  class ComputeWinRate {

    @Test
    void givenAllWinningTrades_whenComputeWinRate_thenOne() {
      List<VirtualPortfolioTrade> trades = List.of(
          tradeWithPnl(100.0), tradeWithPnl(50.0));

      assertEquals(1.0, analyzer.computeWinRate(trades), 0.001);
    }

    @Test
    void givenHalfWinningTrades_whenComputeWinRate_thenHalf() {
      List<VirtualPortfolioTrade> trades = List.of(
          tradeWithPnl(100.0), tradeWithPnl(-50.0));

      assertEquals(0.5, analyzer.computeWinRate(trades), 0.001);
    }

    @Test
    void givenNoTrades_whenComputeWinRate_thenZero() {
      assertEquals(0.0, analyzer.computeWinRate(List.of()));
    }
  }

  @Nested
  class ComputeMaxDrawdown {

    @Test
    void givenMonotonicPnl_whenComputeMaxDrawdown_thenZero() {
      List<VirtualPortfolioTrade> trades = List.of(
          tradeWithPnl(100.0), tradeWithPnl(50.0), tradeWithPnl(30.0));

      assertEquals(0.0, analyzer.computeMaxDrawdown(trades), 0.001);
    }

    @Test
    void givenDrawdownSequence_whenComputeMaxDrawdown_thenCorrectValue() {
      List<VirtualPortfolioTrade> trades = List.of(
          tradeWithPnl(100.0), tradeWithPnl(-80.0), tradeWithPnl(50.0));

      double dd = analyzer.computeMaxDrawdown(trades);

      assertEquals(80.0, dd, 0.001);
    }

    @Test
    void givenNoTrades_whenComputeMaxDrawdown_thenZero() {
      assertEquals(0.0, analyzer.computeMaxDrawdown(List.of()));
    }
  }

  @Nested
  class BuildVerificationProgress {

    @Test
    void given45DaysElapsed_whenBuildVerification_thenDaysNotMet() {
      String json = analyzer.buildVerificationProgress(
          LocalDate.of(2026, 3, 1), 45, 15, 0.58, 0.72, 5000.0);

      assertTrue(json.contains("\"met\": false"));
      assertTrue(json.contains("\"allCriteriaMet\": false"));
    }

    @Test
    void given90DaysAndGoodMetrics_whenBuildVerification_thenAllMet() {
      String json = analyzer.buildVerificationProgress(
          LocalDate.of(2026, 3, 1), 90, 55, 0.60, 0.80, 5000.0);

      assertTrue(json.contains("\"allCriteriaMet\": true"));
    }

    @Test
    void givenNullStartDate_whenBuildVerification_thenEmptyString() {
      String json = analyzer.buildVerificationProgress(
          null, 0, 0, 0.0, 0.0, 0.0);

      assertTrue(json.contains("\"startDate\": \"\""));
    }
  }

  @Nested
  class GenerateReport {

    @Test
    void givenSignalsAndTrades_whenGenerateReport_thenReportPersisted() {
      when(signalLogRepository.findByCreatedAtBetween(any(Instant.class), any(Instant.class)))
          .thenReturn(List.of());
      when(tradeRepository.countByTradeType("PAPER")).thenReturn(0);
      when(tradeRepository.findLatest(0, 0)).thenReturn(List.of());
      when(reportRepository.save(any(), anyInt(), anyInt(), any(), any(), any(), any(),
          any(), any(), any(), any(), any(), anyString())).thenReturn(1L);

      SignalQualityAnalyzer.SignalQualityReportData report =
          analyzer.generateReport(LocalDate.of(2026, 5, 30));

      assertNotNull(report);
      assertEquals(LocalDate.of(2026, 5, 30), report.reportDate());
      verify(reportRepository).save(any(), anyInt(), anyInt(), any(), any(), any(), any(),
          any(), any(), any(), any(), any(), anyString());
    }
  }

  private VirtualPortfolioTrade tradeWithPnl(double pnl) {
    return new VirtualPortfolioTrade(
        null, Instant.now(), "SPY", "SELL", 10.0, 500.0, 0, 0, pnl, null, null, "PAPER");
  }
}
