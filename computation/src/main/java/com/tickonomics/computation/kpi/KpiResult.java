package com.tickonomics.computation.kpi;

public record KpiResult(
        String name,
        double value,
        String status,
        String unit,
        String description
) {
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_ELEVATED = "ELEVATED";
    public static final String STATUS_STRESSED = "STRESSED";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
}
