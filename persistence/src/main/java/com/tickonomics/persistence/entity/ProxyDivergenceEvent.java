package com.tickonomics.persistence.entity;

import java.time.Instant;

public record ProxyDivergenceEvent(
    Instant detectedAt,
    double sofrValue,
    double tbillProxyValue,
    Double correlation5d,
    double divergenceScore,
    String resolution,
    Instant resolvedAt) {}
