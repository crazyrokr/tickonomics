package com.tickonomics.computation.governance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Formal signal decomposition with admissibility checks.
 * Evaluates trading signals against governance rules and returns
 * a verdict indicating whether the signal is admissible or suppressed.
 */
@Service
public class ParticipationGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationGovernanceService.class);

    static final double PROXY_DISLOCATION_THRESHOLD = 2.0;

    private final ConcurrentHashMap<String, AtomicLong> suppressionCounters = new ConcurrentHashMap<>();

    /**
     * Evaluates a signal against governance rules and returns an admissibility verdict.
     *
     * @param signalStrength   raw signal strength value
     * @param proxyDivergence  divergence between proxy and primary signal
     * @param regimeType       current regime type (e.g. HIGH_VOL, UNSTABLE, LOW_VOL, METASTABLE)
     * @param ambiguityFlag    whether ambiguity was detected in the ILI
     * @param exogenousShock   whether an exogenous shock event is active
     * @return GovernanceVerdict with admissibility, suppression reason, and adjusted signal
     */
    GovernanceVerdict evaluate(double signalStrength, double proxyDivergence,
                               String regimeType, boolean ambiguityFlag,
                               boolean exogenousShock) {

        if (proxyDivergence > PROXY_DISLOCATION_THRESHOLD) {
            return suppress("PROXY_DISLOCATION");
        }

        if ("HIGH_VOL".equals(regimeType) || "UNSTABLE".equals(regimeType)) {
            return suppress("DEGRADED_ILI");
        }

        if ("UNKNOWN".equals(regimeType)) {
            return suppress("REGIME_CHECK_FAILURE");
        }

        if (ambiguityFlag) {
            return suppress("AMBIGUITY");
        }

        if (exogenousShock) {
            return suppress("EXOGENOUS_SHOCK");
        }

        log.debug("Signal admitted: strength={}, regime={}", signalStrength, regimeType);
        return new GovernanceVerdict(true, null, signalStrength);
    }

    private GovernanceVerdict suppress(String reason) {
        incrementCounter(reason);
        log.info("Signal suppressed: reason={}", reason);
        return new GovernanceVerdict(false, reason, 0.0);
    }

    private void incrementCounter(String reason) {
        suppressionCounters
                .computeIfAbsent(reason, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    /**
     * Returns a snapshot of suppression counters keyed by reason.
     *
     * @return unmodifiable copy of suppression counters
     */
    Map<String, Long> getSuppressionCounts() {
        Map<String, Long> snapshot = new java.util.HashMap<>();
        suppressionCounters.forEach((key, counter) -> snapshot.put(key, counter.get()));
        return Map.copyOf(snapshot);
    }

    /**
     * Record carrying the governance evaluation result.
     *
     * @param admissible        whether the signal passed governance checks
     * @param suppressionReason reason for suppression, or null if admissible
     * @param adjustedSignal    the adjusted signal value (0.0 if suppressed)
     */
    record GovernanceVerdict(boolean admissible, String suppressionReason,
                             double adjustedSignal) {
    }
}
