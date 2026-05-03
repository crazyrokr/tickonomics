package com.tickonomics.computation.scenario;

import java.util.Map;

public record CrisisProfile(
    String name,
    Map<String, Double> conditionThresholds,
    AumfStatus maxStatus,
    String description) {

    public static final CrisisProfile COVID_2020 = new CrisisProfile(
            "COVID-2020",
            Map.of("volatility_zscore", 3.0, "spread_widening_bps", 50.0, "rrp_drain_velocity", -10.0),
            AumfStatus.SUSPENDED_UNCERTAINTY,
            "COVID-19 pandemic market shock March 2020");

    public static final CrisisProfile SNB_2015 = new CrisisProfile(
            "SNB-2015",
            Map.of("volatility_zscore", 4.0, "spread_widening_bps", 100.0),
            AumfStatus.SAFE_MODE,
            "Swiss franc depeg January 2015");

    public static final CrisisProfile BLACK_MONDAY_1987 = new CrisisProfile(
            "BLACK_MONDAY-1987",
            Map.of("volatility_zscore", 5.0, "spread_widening_bps", 200.0, "liquidity_stress_index", 2.0),
            AumfStatus.SUSPENDED_UNCERTAINTY,
            "Black Monday crash October 1987");
}
