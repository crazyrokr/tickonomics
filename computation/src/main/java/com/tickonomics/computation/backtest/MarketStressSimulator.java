package com.tickonomics.computation.backtest;

import com.tickonomics.computation.scenario.AumfStatus;
import com.tickonomics.computation.scenario.CrisisProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MarketStressSimulator {

  private static final List<CrisisProfile> PROFILES = List.of(
      CrisisProfile.COVID_2020,
      CrisisProfile.SNB_2015,
      CrisisProfile.BLACK_MONDAY_1987);

  public StressTestResult applyShock(BacktestResult baseline, CrisisProfile profile) {
    double volatilityMultiplier = extractVolatilityMultiplier(profile);
    double spreadMultiplier = extractSpreadMultiplier(profile);

    double stressedSharpe = baseline.sharpeRatio() / volatilityMultiplier;
    double stressedDrawdown = baseline.maxDrawdown() * volatilityMultiplier;
    double stressedWinRate = Math.max(0, baseline.winRate() / spreadMultiplier);
    double stressedReturn = baseline.totalReturn() / volatilityMultiplier;

    double sharpeDegradation = baseline.sharpeRatio() != 0
        ? (baseline.sharpeRatio() - stressedSharpe) / Math.abs(baseline.sharpeRatio())
        : 0.0;
    double drawdownIncrease = baseline.maxDrawdown() != 0
        ? (stressedDrawdown - baseline.maxDrawdown()) / Math.abs(baseline.maxDrawdown())
        : 0.0;

    BacktestResult stressedResult = new BacktestResult(
        baseline.strategyName(),
        baseline.delay(),
        stressedSharpe,
        stressedReturn,
        stressedDrawdown,
        stressedWinRate,
        baseline.idealReturn() / volatilityMultiplier,
        baseline.adjustedReturn() / volatilityMultiplier,
        baseline.slippageCostBps() * spreadMultiplier,
        true,
        baseline.tradeLog());

    AumfStatus worstStatus = sharpeDegradation > 0.5
        ? AumfStatus.SUSPENDED_UNCERTAINTY
        : sharpeDegradation > 0.2
            ? AumfStatus.SAFE_MODE
            : AumfStatus.PROCEED_CAUTIOUSLY;

    return new StressTestResult(
        profile.name(),
        baseline,
        stressedResult,
        sharpeDegradation,
        drawdownIncrease,
        worstStatus);
  }

  public Map<String, StressTestResult> runAllShocks(BacktestResult baseline) {
    Map<String, StressTestResult> results = new LinkedHashMap<>();
    for (CrisisProfile profile : PROFILES) {
      results.put(profile.name(), applyShock(baseline, profile));
    }
    return results;
  }

  private double extractVolatilityMultiplier(CrisisProfile profile) {
    Double volZscore = profile.conditionThresholds().get("volatility_zscore");
    return volZscore != null ? volZscore : 1.0;
  }

  private double extractSpreadMultiplier(CrisisProfile profile) {
    Double spreadBps = profile.conditionThresholds().get("spread_widening_bps");
    return spreadBps != null ? spreadBps / 50.0 : 1.0;
  }

  public record StressTestResult(
      String scenarioName,
      BacktestResult baseline,
      BacktestResult stressed,
      double sharpeDegradation,
      double drawdownIncrease,
      AumfStatus worstStatus) {}
}
