package com.tickonomics.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for {@code monitor.datahub.*}. */
@ConfigurationProperties(prefix = "monitor.datahub")
public record DatahubProperties(boolean enabled, String baseUrl, boolean startupFullLoad, String incrementalCheckCron) {
}
