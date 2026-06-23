package com.tickonomics.persistence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds applied to range-scanning repository queries (the {@code findBy*Between} family) so an
 * unbounded time range can never load an arbitrary number of rows. The default cap is overridable
 * via {@code persistence.query.default-limit}; callers needing explicit pagination use the
 * {@code limit}/{@code offset} overloads and {@code PaginatedResponse}.
 */
@ConfigurationProperties(prefix = "persistence.query")
public record QueryLimits(int defaultLimit) {

  static final int FALLBACK_DEFAULT_LIMIT = 10_000;

  public QueryLimits {
    if (defaultLimit <= 0) {
      defaultLimit = FALLBACK_DEFAULT_LIMIT;
    }
  }
}
