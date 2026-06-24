package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.IdempotentRow;
import com.tickonomics.persistence.entity.NewsEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class NewsEventRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String SELECT_COLUMNS =
      "time, event_id, source, headline, avg_tone, themes, actors, url";

  private static final String INSERT_SQL =
      "INSERT INTO news_events (time, event_id, source, headline, avg_tone, themes, actors, url) "
          + "VALUES (:time, :eventId, :source, :headline, :avgTone, :themes, :actors, :url)";

  private static final String IDEMPOTENT_INSERT_SQL =
      "INSERT INTO news_events (time, event_id, source, headline, avg_tone, themes, actors, url, idempotency_key) "
          + "VALUES (:time, :eventId, :source, :headline, :avgTone, :themes, :actors, :url, :idempotencyKey) "
          + "ON CONFLICT (time, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING";

  public NewsEventRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(NewsEvent event) {
    jdbc.update(INSERT_SQL, toParams(event));
  }

  public void saveAll(List<NewsEvent> events) {
    jdbc.batchUpdate(
        INSERT_SQL,
        events.stream().map(this::toParams).toArray(SqlParameterSource[]::new));
  }

  /**
   * Batch insert with durable idempotency-key deduplication. Returns the per-row JDBC affected-row
   * counts ({@code 0} where a duplicate key was suppressed by {@code ON CONFLICT DO NOTHING}).
   */
  public int[] saveAllIdempotent(List<IdempotentRow<NewsEvent>> rows) {
    if (rows == null || rows.isEmpty()) {
      return new int[0];
    }
    return jdbc.batchUpdate(
        IDEMPOTENT_INSERT_SQL,
        rows.stream().map(this::toIdempotentParams).toArray(SqlParameterSource[]::new));
  }

  public List<NewsEvent> findRecent(Instant from, int limit) {
    return jdbc.query(
        "SELECT " + SELECT_COLUMNS + " FROM news_events "
            + "WHERE time >= :from ORDER BY time DESC LIMIT :limit",
        Map.of("from", from, "limit", limit),
        (rs, rowNum) -> new NewsEvent(
            rs.getTimestamp("time").toInstant(),
            rs.getString("event_id"),
            rs.getString("source"),
            rs.getString("headline"),
            rs.getDouble("avg_tone"),
            rs.getString("themes"),
            rs.getString("actors"),
            rs.getString("url")));
  }

  private MapSqlParameterSource toParams(NewsEvent event) {
    return new MapSqlParameterSource()
        .addValue("time", event.time())
        .addValue("eventId", event.eventId())
        .addValue("source", event.source())
        .addValue("headline", event.headline())
        .addValue("avgTone", event.avgTone())
        .addValue("themes", event.themes())
        .addValue("actors", event.actors())
        .addValue("url", event.url());
  }

  private MapSqlParameterSource toIdempotentParams(IdempotentRow<NewsEvent> row) {
    return toParams(row.row()).addValue("idempotencyKey", row.idempotencyKey());
  }
}
