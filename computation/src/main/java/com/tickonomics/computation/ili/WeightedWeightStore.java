package com.tickonomics.computation.ili;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WeightedWeightStore {

    private static final double[] DEFAULT_BASE_WEIGHTS = {0.4, 0.35, 0.25};

    private final double[] baseWeights;
    private final AtomicReference<double[]> calibratedWeights;
    private final double maxDriftPct;

    public WeightedWeightStore() {
        this(DEFAULT_BASE_WEIGHTS, 10.0);
    }

    public WeightedWeightStore(double[] baseWeights, double maxDriftPct) {
        this.baseWeights = Arrays.copyOf(baseWeights, baseWeights.length);
        this.calibratedWeights = new AtomicReference<>(Arrays.copyOf(baseWeights, baseWeights.length));
        this.maxDriftPct = maxDriftPct;
    }

    public double[] getBaseWeights() {
        return Arrays.copyOf(baseWeights, baseWeights.length);
    }

    public double[] getCalibratedWeights() {
        return Arrays.copyOf(calibratedWeights.get(), calibratedWeights.get().length);
    }

    public boolean applyDelta(double[] deltas) {
        if (deltas == null || deltas.length != baseWeights.length) {
            return false;
        }

        double[] current = calibratedWeights.get();
        double[] updated = new double[current.length];

        for (int i = 0; i < current.length; i++) {
            double maxDrift = baseWeights[i] * maxDriftPct / 100.0;
            double clamped = Math.max(-maxDrift, Math.min(maxDrift, deltas[i]));
            updated[i] = current[i] + clamped;
        }

        normalize(updated);

        double[] expected = current;
        return calibratedWeights.compareAndSet(expected, updated);
    }

    public void resetToBase() {
        calibratedWeights.set(Arrays.copyOf(baseWeights, baseWeights.length));
    }

    private void normalize(double[] weights) {
        double sum = Arrays.stream(weights).sum();
        if (sum <= 0) {
            System.arraycopy(baseWeights, 0, weights, 0, baseWeights.length);
            return;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] = weights[i] / sum;
        }
    }
}
