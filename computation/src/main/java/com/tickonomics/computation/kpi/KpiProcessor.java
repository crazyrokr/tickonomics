package com.tickonomics.computation.kpi;

import com.tickonomics.persistence.repository.RateSnapshotRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class KpiProcessor {

    private final NormalizationService normalizationService;
    private final RateSnapshotRepository rateRepository;

    public KpiProcessor(NormalizationService normalizationService,
                        RateSnapshotRepository rateRepository) {
        this.normalizationService = normalizationService;
        this.rateRepository = rateRepository;
    }

    public KpiResult computeLiquidityStressIndex() {
        var rrpRates = getRecentRates("RRP", 60);
        var spreadRates = getRecentRates("SOFR", 60);

        if (rrpRates.isEmpty() || spreadRates.isEmpty()) {
            return new KpiResult("Liquidity Stress Index", Double.NaN, KpiResult.STATUS_UNKNOWN, "index", "Insufficient data");
        }

        double rrpLatest = rrpRates.get(rrpRates.size() - 1);
        double rrpMean = mean(rrpRates);
        double rrpStd = stdDev(rrpRates, rrpMean);

        double zRrp = rrpStd > 0 ? (rrpLatest - rrpMean) / rrpStd : 0.0;

        double stressIndex = -zRrp;
        String status = classifyStress(stressIndex);

        return new KpiResult("Liquidity Stress Index", round(stressIndex), status, "z-score",
                "Negative = tight liquidity (RRP drain)");
    }

    public KpiResult computeRepoEquityBeta(CorrelationEngine correlationEngine) {
        if (correlationEngine == null) {
            return new KpiResult("Repo/Equity Beta", Double.NaN, KpiResult.STATUS_UNKNOWN, "beta", "Correlation engine not available");
        }
        var result = correlationEngine.computeRollingBeta("REPO", "SPY", 60);
        if (!result.valid()) {
            return new KpiResult("Repo/Equity Beta", Double.NaN, KpiResult.STATUS_UNKNOWN, "beta", "Insufficient data");
        }

        double beta = result.latest();
        String status = Math.abs(beta) > 1.5 ? KpiResult.STATUS_ELEVATED : KpiResult.STATUS_NORMAL;

        return new KpiResult("Repo/Equity Beta", round(beta), status, "beta",
                "Sensitivity of equity to repo rate changes");
    }

    public KpiResult computeRrpDrainVelocity() {
        var rates = getRecentRates("RRP", 30);
        if (rates.size() < 5) {
            return new KpiResult("RRP Drain Velocity", Double.NaN, KpiResult.STATUS_UNKNOWN, "bpd", "Insufficient data");
        }

        double velocity = computeDrainVelocity(rates);
        String status = velocity < -5.0 ? KpiResult.STATUS_STRESSED
                : velocity < -2.0 ? KpiResult.STATUS_ELEVATED : KpiResult.STATUS_NORMAL;

        return new KpiResult("RRP Drain Velocity", round(velocity), status, "bps/day",
                "Rate of RRP facility usage change");
    }

    public KpiResult computeVolatilityRegime() {
        var rates = getRecentRates("SOFR", 60);
        if (rates.size() < 10) {
            return new KpiResult("Volatility Regime", Double.NaN, KpiResult.STATUS_UNKNOWN, "regime", "Insufficient data");
        }

        double[] returns = computeReturns(rates);
        double vol = stdDev(returns) * Math.sqrt(252) * 100;

        String regime;
        if (vol < 25) regime = "LOW_VOL";
        else if (vol < 50) regime = "NORMAL";
        else if (vol < 75) regime = "HIGH_VOL";
        else regime = "EXTREME";

        return new KpiResult("Volatility Regime", round(vol), regime, "%",
                "Annualized rate volatility regime");
    }

    public KpiResult computeEfficiencyGap() {
        var rrpRates = getRecentRates("RRP", 20);
        var tgaRates = getRecentRates("TGA", 20);

        if (rrpRates.isEmpty() || tgaRates.isEmpty()) {
            return new KpiResult("Efficiency Gap", Double.NaN, KpiResult.STATUS_UNKNOWN, "index", "Insufficient data");
        }

        double rrpChange = lastChange(rrpRates);
        double tgaChange = lastChange(tgaRates);
        double gap = Math.abs(rrpChange + tgaChange);

        String status = gap > 10 ? KpiResult.STATUS_STRESSED
                : gap > 5 ? KpiResult.STATUS_ELEVATED : KpiResult.STATUS_NORMAL;

        return new KpiResult("Efficiency Gap", round(gap), status, "bps",
                "RRP + TGA drain vs Fed balance sheet change");
    }

    public KpiResult computeSystemicRiskHeatmap() {
        var rrp = getRecentRates("RRP", 20);
        var sofr = getRecentRates("SOFR", 20);
        var effr = getRecentRates("EFFR", 20);

        int signals = 0;
        if (!rrp.isEmpty() && isStressed(rrp)) signals++;
        if (!sofr.isEmpty() && isStressed(sofr)) signals++;
        if (!effr.isEmpty() && isStressed(effr)) signals++;

        double score = signals / 3.0 * 100;
        String status = score > 66 ? KpiResult.STATUS_STRESSED
                : score > 33 ? KpiResult.STATUS_ELEVATED : KpiResult.STATUS_NORMAL;

        return new KpiResult("Systemic Risk Heatmap", round(score), status, "%",
                "Composite stress signal across rate markets");
    }

    @Bulkhead(name = "computationEngine")
    public Map<String, KpiResult> computeAll(CorrelationEngine correlationEngine) {
        Map<String, KpiResult> results = new LinkedHashMap<>();
        results.put("liquidityStress", computeLiquidityStressIndex());
        results.put("repoEquityBeta", computeRepoEquityBeta(correlationEngine));
        results.put("rrpDrainVelocity", computeRrpDrainVelocity());
        results.put("volatilityRegime", computeVolatilityRegime());
        results.put("efficiencyGap", computeEfficiencyGap());
        results.put("systemicRiskHeatmap", computeSystemicRiskHeatmap());
        return results;
    }

    public KpiResult computeReturnGap(double investorGrossReturn, double holdingsReturn) {
        double gap = investorGrossReturn - holdingsReturn;
        String status = gap > 0.005 ? KpiResult.STATUS_ELEVATED
                : gap < -0.005 ? KpiResult.STATUS_STRESSED : KpiResult.STATUS_NORMAL;

        return new KpiResult("Return Gap", round(gap), status, "bps",
                "Execution alpha vs buy-and-hold return");
    }

    List<Double> getRecentRates(String rateType, int days) {
        try {
            var to = Instant.now();
            var from = to.minus(Duration.ofDays(days));
            var snapshots = rateRepository.findByRateTypeAndTimeBetween(rateType, from, to);
            return snapshots.stream().map(s -> s.value()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    double computeDrainVelocity(List<Double> rates) {
        if (rates.size() < 2) return 0.0;
        int n = rates.size();
        double firstHalf = mean(rates.subList(0, n / 2));
        double secondHalf = mean(rates.subList(n / 2, n));
        return (secondHalf - firstHalf) * 10000 / (n / 2.0);
    }

    double[] computeReturns(List<Double> prices) {
        double[] returns = new double[prices.size() - 1];
        for (int i = 1; i < prices.size(); i++) {
            returns[i - 1] = (prices.get(i) - prices.get(i - 1)) / prices.get(i - 1);
        }
        return returns;
    }

    boolean isStressed(List<Double> rates) {
        if (rates.size() < 5) return false;
        double m = mean(rates);
        double s = stdDev(rates, m);
        if (s == 0) return false;
        double latest = rates.get(rates.size() - 1);
        double z = (latest - m) / s;
        return Math.abs(z) > 2.0;
    }

    double mean(List<Double> values) {
        return values.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    double stdDev(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }

    double stdDev(double[] values) {
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        double variance = 0;
        for (double v : values) variance += Math.pow(v - mean, 2);
        return Math.sqrt(variance / values.length);
    }

    double lastChange(List<Double> values) {
        if (values.size() < 2) return 0.0;
        return (values.get(values.size() - 1) - values.get(values.size() - 2)) * 10000;
    }

    String classifyStress(double index) {
        if (index > 1.5) return KpiResult.STATUS_STRESSED;
        if (index > 0.5) return KpiResult.STATUS_ELEVATED;
        return KpiResult.STATUS_NORMAL;
    }

    double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
