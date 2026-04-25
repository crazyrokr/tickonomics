package com.tickonomics.computation.fixedincome;

import java.util.List;

public record FixedIncomePortfolio(
    FixedIncomeStrategyType type,
    List<BondPosition> positions,
    double portfolioDuration,
    double portfolioYield,
    double convexity
) {}
