package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.ProxyDivergenceEvent;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProxyDivergenceEventRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public ProxyDivergenceEventRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(ProxyDivergenceEvent event) {
    jdbc.update(
        "INSERT INTO proxy_divergence_events (detected_at, sofr_value, tbill_proxy_value, correlation_5d, "
            + "divergence_score, resolution, resolved_at) "
            + "VALUES (:detectedAt, :sofrValue, :tbillProxyValue, :correlation5d, :divergenceScore, :resolution, "
            + ":resolvedAt)",
        toParams(event));
  }

  public List<ProxyDivergenceEvent> findUnresolved() {
    return jdbc.query(
        "SELECT detected_at, sofr_value, tbill_proxy_value, correlation_5d, divergence_score, resolution, resolved_at "
            + "FROM proxy_divergence_events WHERE resolution IS NULL ORDER BY detected_at DESC",
        Map.of(),
        (rs, rowNum) -> new ProxyDivergenceEvent(
            rs
                .getTimestamp("detected_at")
                .toInstant(),
            rs.getDouble("sofr_value"),
            rs.getDouble("tbill_proxy_value"),
            rs.getDouble("correlation_5d"),
            rs.getDouble("divergence_score"),
            rs.getString("resolution"),
            rs.getTimestamp("resolved_at") != null ? rs
                .getTimestamp("resolved_at")
                .toInstant() : null));
  }

  public void resolve(long id, String resolution) {
    jdbc.update(
        "UPDATE proxy_divergence_events SET resolution = :resolution, resolved_at = NOW() WHERE id = :id",
        Map.of("id", id, "resolution", resolution));
  }

  private MapSqlParameterSource toParams(ProxyDivergenceEvent event) {
    return new MapSqlParameterSource()
        .addValue("detectedAt", event.detectedAt())
        .addValue("sofrValue", event.sofrValue())
        .addValue("tbillProxyValue", event.tbillProxyValue())
        .addValue("correlation5d", event.correlation5d())
        .addValue("divergenceScore", event.divergenceScore())
        .addValue("resolution", event.resolution())
        .addValue("resolvedAt", event.resolvedAt());
  }
}
