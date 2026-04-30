package com.tickonomics.computation.kpi;

public record ZscoreResult(
        String component,
        double rawValue,
        double zScore,
        double mean,
        double stdDev,
        int lookbackDays,
        boolean valid
) {
    public static ZscoreResult invalid(String component, double rawValue, int lookbackDays) {
        return new ZscoreResult(component, rawValue, Double.NaN, Double.NaN, Double.NaN, lookbackDays, false);
    }

    public static ZscoreResult of(String component, double rawValue, double mean, double stdDev, int lookbackDays) {
        if (stdDev == 0.0 || Double.isNaN(stdDev)) {
            return invalid(component, rawValue, lookbackDays);
        }
        double z = (rawValue - mean) / stdDev;
        return new ZscoreResult(component, rawValue, z, mean, stdDev, lookbackDays, true);
    }
}
