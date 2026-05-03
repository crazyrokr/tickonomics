package com.tickonomics.computation.scenario;

import com.tickonomics.computation.kpi.KpiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AumfScenarioEngine {

    private static final Logger log = LoggerFactory.getLogger(AumfScenarioEngine.class);
    private static final List<CrisisProfile> PROFILES = List.of(
            CrisisProfile.COVID_2020, CrisisProfile.SNB_2015, CrisisProfile.BLACK_MONDAY_1987);

    public AumfResult evaluate(Map<String, KpiResult> currentKpis) {
        if (currentKpis == null || currentKpis.isEmpty()) {
            return new AumfResult(AumfStatus.NORMAL, "No KPI data available", 0.0, null);
        }

        AumfStatus worstStatus = AumfStatus.NORMAL;
        CrisisProfile matchedProfile = null;
        double maxMatchScore = 0.0;

        for (CrisisProfile profile : PROFILES) {
            double matchScore = computeMatchScore(profile, currentKpis);
            if (matchScore > maxMatchScore) {
                maxMatchScore = matchScore;
                matchedProfile = profile;
            }

            if (matchScore > 0.7 && profile.maxStatus().ordinal() > worstStatus.ordinal()) {
                worstStatus = profile.maxStatus();
            }
        }

        if (maxMatchScore > 0.3 && maxMatchScore <= 0.7 && worstStatus == AumfStatus.NORMAL) {
            worstStatus = AumfStatus.PROCEED_CAUTIOUSLY;
        }

        log.debug("AUMF evaluation: status={}, matchScore={}, profile={}",
                worstStatus, maxMatchScore, matchedProfile != null ? matchedProfile.name() : "none");

        return new AumfResult(worstStatus,
                matchedProfile != null ? matchedProfile.description() : "Normal market conditions",
                maxMatchScore, matchedProfile);
    }

    double computeMatchScore(CrisisProfile profile, Map<String, KpiResult> kpis) {
        Map<String, Double> thresholds = profile.conditionThresholds();
        int matched = 0;
        int total = thresholds.size();

        for (Map.Entry<String, Double> condition : thresholds.entrySet()) {
            String metric = condition.getKey();
            double threshold = condition.getValue();

            if (isConditionMet(metric, threshold, kpis)) {
                matched++;
            }
        }

        return total > 0 ? (double) matched / total : 0.0;
    }

    boolean isConditionMet(String metric, double threshold, Map<String, KpiResult> kpis) {
        return switch (metric) {
            case "volatility_zscore" -> {
                var vol = kpis.get("volatilityRegime");
                yield vol != null && Math.abs(vol.value()) > threshold * 15;
            }
            case "spread_widening_bps" -> {
                var gap = kpis.get("efficiencyGap");
                yield gap != null && gap.value() > threshold;
            }
            case "rrp_drain_velocity" -> {
                var rrp = kpis.get("rrpDrainVelocity");
                yield rrp != null && rrp.value() < threshold;
            }
            case "liquidity_stress_index" -> {
                var lsi = kpis.get("liquidityStress");
                yield lsi != null && lsi.value() > threshold;
            }
            default -> false;
        };
    }

    public record AumfResult(AumfStatus status, String description, double matchScore, CrisisProfile matchedProfile) {}
}
