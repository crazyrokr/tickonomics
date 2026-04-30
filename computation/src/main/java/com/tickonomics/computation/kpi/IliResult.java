package com.tickonomics.computation.kpi;

public record IliResult(
        double iliValue,
        double zRrp,
        double zSpread,
        double zVol,
        String dataStatus,
        double[] activeWeights,
        String proxyDivergenceStatus,
        Double proxyDivergenceScore
) {
    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_DEGRADED = "DEGRADED_COMPONENT_STALE";
    public static final String STATUS_DISLOCATED = "DISLOCATED";

    public IliResult {
        if (activeWeights != null) {
            double sum = 0;
            for (double w : activeWeights) {
                sum += w;
            }
            if (Math.abs(sum - 1.0) > 0.01) {
                throw new IllegalArgumentException("Weights must sum to 1.0, got " + sum);
            }
        }
    }
}
