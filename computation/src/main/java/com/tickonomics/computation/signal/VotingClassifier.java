package com.tickonomics.computation.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VotingClassifier {

    private static final Logger log = LoggerFactory.getLogger(VotingClassifier.class);

    private boolean enabled;

    public record VoteResult(
            String actionable,
            String iliSignal,
            String mlSignal,
            int agreementCount,
            String recommendation) {}

    public VotingClassifier() {
        this(false);
    }

    public VotingClassifier(boolean enabled) {
        this.enabled = enabled;
    }

    public VoteResult vote(String iliSignal, String mlSignal) {
        if (!enabled) {
            log.debug("VotingClassifier disabled, passing through ILI signal: {}", iliSignal);
            return new VoteResult(iliSignal, iliSignal, mlSignal, 1, "DISABLED_PASSTHROUGH");
        }

        String actionable = resolveAction(iliSignal, mlSignal);
        int agreementCount = countAgreements(iliSignal, mlSignal);
        String recommendation = buildRecommendation(iliSignal, mlSignal, actionable);

        log.info("Vote result: ILI={}, ML={}, actionable={}, agreement={}",
                iliSignal, mlSignal, actionable, agreementCount);

        return new VoteResult(actionable, iliSignal, mlSignal, agreementCount, recommendation);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("VotingClassifier enabled={}", enabled);
    }

    String resolveAction(String iliSignal, String mlSignal) {
        if (iliSignal == null || mlSignal == null) {
            return "INSUFFICIENT_CONVICTION";
        }

        String iliNorm = iliSignal.toUpperCase().trim();
        String mlNorm = mlSignal.toUpperCase().trim();

        if ("NEUTRAL".equals(iliNorm) || "NEUTRAL".equals(mlNorm)) {
            return "INSUFFICIENT_CONVICTION";
        }

        if ("BUY".equals(iliNorm) && "BUY".equals(mlNorm)) {
            return "ACTIONABLE_BUY";
        }

        if ("SELL".equals(iliNorm) && "SELL".equals(mlNorm)) {
            return "ACTIONABLE_SELL";
        }

        return "CONFLICT_HOLD";
    }

    int countAgreements(String iliSignal, String mlSignal) {
        if (iliSignal == null || mlSignal == null) {
            return 0;
        }
        return iliSignal.equalsIgnoreCase(mlSignal) ? 2 : 1;
    }

    String buildRecommendation(String iliSignal, String mlSignal, String actionable) {
        return switch (actionable) {
            case "ACTIONABLE_BUY" -> "STRONG_BUY_CONSENSUS";
            case "ACTIONABLE_SELL" -> "STRONG_SELL_CONSENSUS";
            case "CONFLICT_HOLD" -> "SIGNALS_DIVERGE_HOLD";
            case "INSUFFICIENT_CONVICTION" -> "NEUTRAL_SIGNAL_NO_ACTION";
            default -> "NO_RECOMMENDATION";
        };
    }
}
