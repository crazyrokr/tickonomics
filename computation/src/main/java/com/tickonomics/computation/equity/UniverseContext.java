package com.tickonomics.computation.equity;

import java.time.Instant;

public record UniverseContext(
    Instant time,
    double universeMean,
    double universeStd,
    int symbolCount
) {}
