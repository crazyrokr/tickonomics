package com.tickonomics.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.yield-curve.*}. */
@ConfigurationProperties(prefix = "monitor.yield-curve")
public record YieldCurveProperties(boolean enabled) {
}
