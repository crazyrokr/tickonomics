package com.tickonomics.computation.optimization;

import com.tickonomics.computation.ili.WeightedWeightStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ScheduledCalibrationTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCalibrationTask.class);

    static final int TOTAL_WINDOW_DAYS = 180;
    static final int TRAINING_DAYS = 150;
    static final int VALIDATION_DAYS = 30;
    static final double MIN_SHARPE_IMPROVEMENT = 0.1;

    private final WeightedWeightStore weightStore;
    private final ReentrantLock calibrationLock = new ReentrantLock();
    private volatile Instant lastCalibrationTime;

    public ScheduledCalibrationTask(WeightedWeightStore weightStore) {
        this.weightStore = weightStore;
    }

    @Scheduled(cron = "${computation.calibration.cron:0 0 6 1 */1 ?}")
    CalibrationResult runCalibration() {
        if (!calibrationLock.tryLock()) {
            log.info("Calibration already in progress, skipping");
            return new CalibrationResult(false, 0.0, 0.0, 0.0, "SKIPPED_IN_PROGRESS");
        }
        try {
            log.warn("Scheduled calibration has no price history wired; a return-based Sharpe "
                    + "cannot be computed. Invoke runCalibration(priceHistory) or "
                    + "runCalibrationWithHistory(priceHistory) to run a meaningful optimization.");
            return new CalibrationResult(false, 0.0, 0.0, 0.0, "NO_PRICE_DATA");
        } finally {
            calibrationLock.unlock();
        }
    }

    CalibrationResult runCalibration(double[][] priceHistory) {
        if (!calibrationLock.tryLock()) {
            log.info("Calibration already in progress, skipping");
            return new CalibrationResult(false, 0.0, 0.0, 0.0, "SKIPPED_IN_PROGRESS");
        }
        try {
            return doCalibration(priceHistory);
        } finally {
            calibrationLock.unlock();
        }
    }

    CalibrationResult runCalibrationWithHistory(double[][] priceHistory) {
        if (priceHistory == null || priceHistory.length < TOTAL_WINDOW_DAYS) {
            String msg = "Insufficient data: required {} days, got {}";
            log.info(msg, TOTAL_WINDOW_DAYS, priceHistory == null ? 0 : priceHistory.length);
            return new CalibrationResult(false, 0.0, 0.0, 0.0, "INSUFFICIENT_DATA");
        }

        double[][] trainingSlice = sliceRows(priceHistory, 0, TRAINING_DAYS);
        double[][] validationSlice = sliceRows(priceHistory, TRAINING_DAYS, TOTAL_WINDOW_DAYS);

        double[] currentWeights = weightStore.getCalibratedWeights();
        double sharpeBefore = computeSharpeRatio(validationSlice, currentWeights);

        double[] optimizedWeights = gridSearchOptimize(trainingSlice, currentWeights);
        double sharpeAfterInSample = computeSharpeRatio(trainingSlice, optimizedWeights);
        double oosSharpe = computeSharpeRatio(validationSlice, optimizedWeights);

        double improvement = oosSharpe - sharpeBefore;
        boolean improved = improvement >= MIN_SHARPE_IMPROVEMENT;

        if (improved) {
            double[] deltas = new double[optimizedWeights.length];
            for (int i = 0; i < optimizedWeights.length; i++) {
                deltas[i] = optimizedWeights[i] - currentWeights[i];
            }
            weightStore.applyDelta(deltas);
        }

        String status = improved ? "IMPROVED" : "NO_IMPROVEMENT";
        lastCalibrationTime = Instant.now();

        log.info("Calibration result: status={}, sharpeBefore={}, oosSharpe={}, improvement={}",
                status, sharpeBefore, oosSharpe, improvement);

        return new CalibrationResult(improved, sharpeBefore, sharpeAfterInSample, oosSharpe, status);
    }

    private CalibrationResult doCalibration(double[][] priceHistory) {
        if (priceHistory == null || priceHistory.length < 2) {
            return new CalibrationResult(false, 0.0, 0.0, 0.0, "INSUFFICIENT_DATA");
        }
        double[] currentWeights = weightStore.getCalibratedWeights();
        double sharpeBefore = computeSharpeRatio(priceHistory, currentWeights);

        double[] optimizedWeights = perturbOptimize(priceHistory, currentWeights);
        double sharpeAfter = computeSharpeRatio(priceHistory, optimizedWeights);

        double improvement = sharpeAfter - sharpeBefore;
        boolean improved = improvement >= MIN_SHARPE_IMPROVEMENT;

        if (improved) {
            double[] deltas = new double[optimizedWeights.length];
            for (int i = 0; i < optimizedWeights.length; i++) {
                deltas[i] = optimizedWeights[i] - currentWeights[i];
            }
            weightStore.applyDelta(deltas);
        }

        String status = improved ? "IMPROVED" : "NO_IMPROVEMENT";
        lastCalibrationTime = Instant.now();

        log.info("Calibration completed: status={}, sharpeBefore={}, sharpeAfter={}, improvement={}",
                status, sharpeBefore, sharpeAfter, improvement);

        return new CalibrationResult(improved, sharpeBefore, sharpeAfter, sharpeAfter, status);
    }

    double[] gridSearchOptimize(double[][] trainingData, double[] currentWeights) {
        double[] bestWeights = Arrays.copyOf(currentWeights, currentWeights.length);
        double bestSharpe = computeSharpeRatio(trainingData, currentWeights);

        int steps = 10;
        int n = currentWeights.length;

        for (int iter = 0; iter < 3; iter++) {
            double[] candidate = Arrays.copyOf(bestWeights, n);
            boolean improved = false;

            for (int i = 0; i < n; i++) {
                double[] testWeights = Arrays.copyOf(candidate, n);
                for (int s = -steps / 2; s <= steps / 2; s++) {
                    testWeights[i] = candidate[i] + s * 0.02;
                    testWeights[i] = Math.max(0.01, testWeights[i]);
                    normalize(testWeights);
                    double sharpe = computeSharpeRatio(trainingData, testWeights);
                    if (sharpe > bestSharpe) {
                        bestSharpe = sharpe;
                        bestWeights = Arrays.copyOf(testWeights, n);
                        improved = true;
                    }
                }
            }

            if (!improved) {
                break;
            }
        }

        normalize(bestWeights);
        return bestWeights;
    }

    double[] perturbOptimize(double[][] priceData, double[] currentWeights) {
        double[] bestWeights = Arrays.copyOf(currentWeights, currentWeights.length);
        double bestSharpe = computeSharpeRatio(priceData, currentWeights);

        for (int trial = 0; trial < 50; trial++) {
            double[] candidate = new double[currentWeights.length];
            double sum = 0;
            for (int i = 0; i < currentWeights.length; i++) {
                candidate[i] = currentWeights[i] + (Math.random() - 0.5) * 0.1;
                candidate[i] = Math.max(0.01, candidate[i]);
                sum += candidate[i];
            }
            for (int i = 0; i < candidate.length; i++) {
                candidate[i] /= sum;
            }

            double sharpe = computeSharpeRatio(priceData, candidate);
            if (sharpe > bestSharpe) {
                bestSharpe = sharpe;
                bestWeights = candidate;
            }
        }

        return bestWeights;
    }

    double computeSharpeRatio(double[][] priceData, double[] weights) {
        if (priceData == null || priceData.length < 2 || weights == null || weights.length == 0) {
            return 0.0;
        }

        int n = priceData.length;
        int m = Math.min(weights.length, priceData[0] == null ? 0 : priceData[0].length);
        if (m == 0) {
            return 0.0;
        }

        double[] portfolioReturns = new double[n - 1];
        for (int t = 1; t < n; t++) {
            double weightedReturn = 0;
            for (int i = 0; i < m; i++) {
                double prev = priceData[t - 1][i];
                double curr = priceData[t][i];
                if (prev != 0) {
                    weightedReturn += weights[i] * (curr - prev) / prev;
                }
            }
            portfolioReturns[t - 1] = weightedReturn;
        }

        return sharpeFromReturns(portfolioReturns);
    }

    private double sharpeFromReturns(double[] returns) {
        if (returns.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (double r : returns) {
            sum += r;
        }
        double mean = sum / returns.length;

        double sumSqDiff = 0;
        for (double r : returns) {
            sumSqDiff += (r - mean) * (r - mean);
        }
        double std = Math.sqrt(sumSqDiff / returns.length);

        return std > 1e-10 ? mean / std : 0.0;
    }

    private double[][] sliceRows(double[][] data, int from, int to) {
        double[][] slice = new double[to - from][];
        System.arraycopy(data, from, slice, 0, to - from);
        return slice;
    }

    private void normalize(double[] weights) {
        double sum = 0;
        for (double w : weights) {
            sum += Math.max(0.0, w);
        }
        if (sum <= 0) {
            Arrays.fill(weights, 1.0 / weights.length);
            return;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.max(0.0, weights[i]) / sum;
        }
    }

    Instant getLastCalibrationTime() {
        return lastCalibrationTime;
    }

    public record CalibrationResult(
            boolean improved,
            double sharpeBefore,
            double sharpeAfter,
            double oosSharpe,
            String status
    ) {}
}
