package com.tickonomics.computation.backtest;

import com.tickonomics.computation.util.StatisticsUtils;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WalkForwardValidator {

    private static final Logger log = LoggerFactory.getLogger(WalkForwardValidator.class);

    private static final int DEFAULT_ANCHOR = 252;
    private static final int DEFAULT_TEST_WINDOW = 63;
    private static final double ANNUALIZATION_FACTOR = Math.sqrt(252);

    public record WfaResult(
            double inSampleSharpe,
            double outOfSampleSharpe,
            double degradation,
            int folds,
            String status) {}

    public record FoldResult(
            int fold,
            double trainSharpe,
            double testSharpe,
            double foldDegradation) {}

    public WalkForwardValidator() {}

    public WfaResult validate(List<Double> returns) {
        return validate(returns, DEFAULT_ANCHOR, DEFAULT_TEST_WINDOW);
    }

    public WfaResult validate(List<Double> returns, int anchorWindow, int testWindow) {
        if (returns == null || returns.size() < anchorWindow + testWindow) {
            log.warn("Insufficient data for WFA: need {} but got {}",
                    anchorWindow + testWindow,
                    returns == null ? 0 : returns.size());
            return new WfaResult(0.0, 0.0, 0.0, 0, "INSUFFICIENT_DATA");
        }

        List<FoldResult> foldResults = computeFoldResults(returns, anchorWindow, testWindow);

        double avgInSample = average(foldResults.stream().map(FoldResult::trainSharpe).toList());
        double avgOutOfSample = average(foldResults.stream().map(FoldResult::testSharpe).toList());

        double degradation = (avgInSample - avgOutOfSample) / Math.max(0.01, Math.abs(avgInSample));
        String status = classifyDegradation(degradation);

        log.info("WFA complete: folds={}, IS Sharpe={:.4f}, OOS Sharpe={:.4f}, degradation={:.4f}, status={}",
                foldResults.size(), avgInSample, avgOutOfSample, degradation, status);

        return new WfaResult(avgInSample, avgOutOfSample, degradation, foldResults.size(), status);
    }

    public List<FoldResult> getFoldResults(List<Double> returns) {
        return getFoldResults(returns, DEFAULT_ANCHOR, DEFAULT_TEST_WINDOW);
    }

    public List<FoldResult> getFoldResults(List<Double> returns, int anchorWindow, int testWindow) {
        if (returns == null || returns.size() < anchorWindow + testWindow) {
            return List.of();
        }
        return computeFoldResults(returns, anchorWindow, testWindow);
    }

    List<FoldResult> computeFoldResults(List<Double> returns, int anchorWindow, int testWindow) {
        List<FoldResult> results = new ArrayList<>();
        int trainStart = 0;
        int trainEnd = anchorWindow;
        int fold = 0;

        while (trainEnd + testWindow <= returns.size()) {
            List<Double> trainSlice = returns.subList(trainStart, trainEnd);
            List<Double> testSlice = returns.subList(trainEnd, Math.min(trainEnd + testWindow, returns.size()));

            double trainSharpe = computeSharpe(trainSlice);
            double testSharpe = computeSharpe(testSlice);
            double foldDeg = (trainSharpe - testSharpe) / Math.max(0.01, Math.abs(trainSharpe));

            fold++;
            results.add(new FoldResult(fold, trainSharpe, testSharpe, foldDeg));

            trainEnd += testWindow;
        }

        return results;
    }

    double computeSharpe(List<Double> returns) {
        if (returns == null || returns.size() < 2) {
            return 0.0;
        }
        return StatisticsUtils.sharpe(returns, 0.0) * ANNUALIZATION_FACTOR;
    }

    String classifyDegradation(double degradation) {
        if (degradation < 0.3) {
            return "GOOD";
        } else if (degradation <= 0.6) {
            return "WARNING";
        }
        return "POOR";
    }

    private double average(List<Double> values) {
        return StatisticsUtils.mean(values);
    }
}
