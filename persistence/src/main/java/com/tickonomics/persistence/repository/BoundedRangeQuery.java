package com.tickonomics.persistence.repository;

import java.util.List;
import org.slf4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Applies a {@code LIMIT}/{@code OFFSET} bound and an at-cap warning to the {@code findBy*Between}
 * family of range queries. Centralizes the pagination clause so every range scan is bounded the
 * same way and truncation is never silent.
 */
public final class BoundedRangeQuery {

  private BoundedRangeQuery() {
    throw new UnsupportedOperationException("utility class");
  }

  /**
   * Runs {@code sql} with {@code params}, appending {@code LIMIT :limit [OFFSET :offset]}.
   * Logs a warning when the result reaches the cap so callers know it may be truncated.
   */
  public static <T> List<T> execute(
      NamedParameterJdbcTemplate jdbc,
      String sql,
      MapSqlParameterSource params,
      RowMapper<T> rowMapper,
      int limit,
      Integer offset,
      Logger logger,
      String queryName) {
    params.addValue("limit", limit);
    boolean hasOffset = offset != null && offset > 0;
    if (hasOffset) {
      params.addValue("offset", offset);
    }
    String boundedSql = sql + (hasOffset ? " LIMIT :limit OFFSET :offset" : " LIMIT :limit");
    List<T> results = jdbc.query(boundedSql, params, rowMapper);
    if (results.size() >= limit) {
      logger.warn(
          "{} returned {} rows (cap={}); result may be truncated. "
              + "Raise persistence.query.default-limit or use the paginated overload.",
          queryName,
          results.size(),
          limit);
    }
    return results;
  }
}
