package com.tickonomics.computation.stress;

import java.time.Instant;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs predefined liquidity stress scenarios against the ILI model
 * to validate robustness under historical crisis conditions.
 */
@Service
public class LiquidityStressTestModule {

    private static final Logger log = LoggerFactory.getLogger(LiquidityStressTestModule.class);

    private static final double MAX_DRAWDOWN_PCT = 15.0;
    private static final double MAX_FALSE_POSITIVE_RATE = 5.0;

    public record StressScenario(
            String name,
            double rrpShockBps,
            double spreadShockBps,
            double volShockMultiplier,
            String description
    ) {}

    public record StressTestResult(
            String scenarioName,
            double drawdownPct,
            double timeToRecoveryDays,
            double falsePositiveRate,
            boolean passed
    ) {}

    static StressScenario swissFranc2015() {
        return new StressScenario(
                "SWISS_FRANC_2015", 200.0, 150.0, 3.0,
                "Swiss National Bank removed EUR/CHF floor, Jan 2015"
        );
    }

    static StressScenario repoSpike2019() {
        return new StressScenario(
                "REPO_SPIKE_2019", 100.0, 200.0, 2.5,
                "US repo market spike, Sep 2019"
        );
    }

    static StressScenario covid2020() {
        return new StressScenario(
                "COVID_2020", 50.0, 300.0, 4.0,
                "COVID-19 liquidity crisis, Mar 2020"
        );
    }

    /**
     * Runs a stress scenario by applying shocks to the current ILI and historical values,
     * then computing drawdown, time-to-recovery, and false positive rate.
     *
     * @param scenario             the stress scenario parameters
     * @param currentIli           the current ILI value
     * @param historicalIliValues  historical ILI observations for baseline statistics
     * @return stress test result with pass/fail verdict
     */
    StressTestResult runScenario(StressScenario scenario, double currentIli, double[] historicalIliValues) {
        log.info("Running stress scenario: {}", scenario.name());

        double shockedIli = applyShocks(scenario, currentIli, historicalIliValues);

        double drawdownPct = computeDrawdown(currentIli, shockedIli, scenario);
        double timeToRecoveryDays = computeRecoveryTime(drawdownPct, scenario);
        double falsePositiveRate = computeFalsePositiveRate(scenario, historicalIliValues);

        boolean passed = drawdownPct < MAX_DRAWDOWN_PCT && falsePositiveRate < MAX_FALSE_POSITIVE_RATE;

        log.info("Scenario {} completed: drawdown={}%, recovery={}d, fpr={}%, passed={}",
                scenario.name(), drawdownPct, timeToRecoveryDays, falsePositiveRate, passed);

        return new StressTestResult(scenario.name(), drawdownPct, timeToRecoveryDays, falsePositiveRate, passed);
    }

    double applyShocks(StressScenario scenario, double currentIli, double[] historicalIliValues) {
        double rrpImpact = scenario.rrpShockBps() / 10000.0;
        double spreadImpact = scenario.spreadShockBps() / 10000.0;
        double historicalMean = computeMean(historicalIliValues);
        double baseContribution = Math.abs(currentIli - historicalMean);
        double spreadComponent = spreadImpact * (1.0 + baseContribution);
        return currentIli - rrpImpact - spreadComponent;
    }

    double computeDrawdown(double currentIli, double shockedIli, StressScenario scenario) {
        double rawDrawdown = Math.abs(currentIli - shockedIli) * 100.0;
        return rawDrawdown * scenario.volShockMultiplier();
    }

    double computeRecoveryTime(double drawdownPct, StressScenario scenario) {
        double baseRecoveryDays = drawdownPct / 5.0;
        return baseRecoveryDays / Math.max(0.1, scenario.volShockMultiplier() - 1.0);
    }

    double computeFalsePositiveRate(StressScenario scenario, double[] historicalIliValues) {
        if (historicalIliValues == null || historicalIliValues.length == 0) {
            return 0.0;
        }

        double threshold = scenario.spreadShockBps() / 100.0;
        long exceedanceCount = Arrays.stream(historicalIliValues)
                .filter(v -> Math.abs(v) > threshold)
                .count();

        return (exceedanceCount / (double) historicalIliValues.length) * 100.0;
    }

    double computeMean(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        return Arrays.stream(values).average().orElse(0.0);
    }
}
