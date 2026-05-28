package com.tickonomics.computation.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SaliProcessor {

    private static final Logger log = LoggerFactory.getLogger(SaliProcessor.class);
    private static final double ILI_WEIGHT = 0.7;
    private static final double SENTIMENT_WEIGHT = 0.3;
    private static final double EXTREME_BULLISH_THRESHOLD = 0.8;

    public enum Action {
        BUY, SELL, HOLD
    }

    public record SaliResult(double saliValue, double iliComponent, double sentimentComponent,
                             Action action, boolean suppressed) {
    }

    public SaliResult process(double iliScore, double sentimentScore) {
        double iliComponent = ILI_WEIGHT * iliScore;
        double sentimentComponent = SENTIMENT_WEIGHT * sentimentScore;
        double saliValue = iliComponent + sentimentComponent;

        Action action = determineAction(iliScore, sentimentScore);
        boolean suppressed = action == Action.HOLD;

        if (action == Action.SELL && sentimentScore > EXTREME_BULLISH_THRESHOLD) {
            action = Action.HOLD;
            suppressed = true;
            log.debug("SELL suppressed: extremely bullish sentiment ({:.2f})", sentimentScore);
        }

        log.debug("SALI: value={:.4f}, ili={:.4f}, sentiment={:.4f}, action={}, suppressed={}",
                saliValue, iliComponent, sentimentComponent, action, suppressed);

        return new SaliResult(saliValue, iliComponent, sentimentComponent, action, suppressed);
    }

    Action determineAction(double iliScore, double sentimentScore) {
        boolean iliBearish = iliScore < 0;
        boolean iliBullish = iliScore > 0;
        boolean sentimentBearish = sentimentScore < 0;
        boolean sentimentBullish = sentimentScore > 0;

        if (iliBearish && sentimentBearish) {
            return Action.SELL;
        }
        if (iliBullish && sentimentBullish) {
            return Action.BUY;
        }
        return Action.HOLD;
    }
}
