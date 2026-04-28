package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.IliHistory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class IliHistoryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IliHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(IliHistory entry) {
        jdbc.update(
                "INSERT INTO ili_history (time, ili_value, z_rrp, z_spread, z_vol, data_status, active_weights, proxy_divergence_status, proxy_divergence_score) " +
                        "VALUES (:time, :iliValue, :zRrp, :zSpread, :zVol, :dataStatus, :activeWeights::jsonb, :proxyDivergenceStatus, :proxyDivergenceScore)",
                toParams(entry));
    }

    public List<IliHistory> findByTimeBetween(Instant from, Instant to) {
        return jdbc.query(
                "SELECT time, ili_value, z_rrp, z_spread, z_vol, data_status, active_weights, proxy_divergence_status, proxy_divergence_score " +
                        "FROM ili_history WHERE time BETWEEN :from AND :to ORDER BY time",
                Map.of("from", from, "to", to),
                (rs, rowNum) -> new IliHistory(
                        rs.getTimestamp("time").toInstant(),
                        rs.getDouble("ili_value"),
                        rs.getDouble("z_rrp"),
                        rs.getDouble("z_spread"),
                        rs.getDouble("z_vol"),
                        rs.getString("data_status"),
                        rs.getString("active_weights"),
                        rs.getString("proxy_divergence_status"),
                        rs.getDouble("proxy_divergence_score")));
    }

    public IliHistory findLatest() {
        return jdbc.queryForObject(
                "SELECT time, ili_value, z_rrp, z_spread, z_vol, data_status, active_weights, proxy_divergence_status, proxy_divergence_score " +
                        "FROM ili_history ORDER BY time DESC LIMIT 1",
                Map.of(),
                (rs, rowNum) -> new IliHistory(
                        rs.getTimestamp("time").toInstant(),
                        rs.getDouble("ili_value"),
                        rs.getDouble("z_rrp"),
                        rs.getDouble("z_spread"),
                        rs.getDouble("z_vol"),
                        rs.getString("data_status"),
                        rs.getString("active_weights"),
                        rs.getString("proxy_divergence_status"),
                        rs.getDouble("proxy_divergence_score")));
    }

    private MapSqlParameterSource toParams(IliHistory entry) {
        return new MapSqlParameterSource()
                .addValue("time", entry.time())
                .addValue("iliValue", entry.iliValue())
                .addValue("zRrp", entry.zRrp())
                .addValue("zSpread", entry.zSpread())
                .addValue("zVol", entry.zVol())
                .addValue("dataStatus", entry.dataStatus())
                .addValue("activeWeights", entry.activeWeights())
                .addValue("proxyDivergenceStatus", entry.proxyDivergenceStatus())
                .addValue("proxyDivergenceScore", entry.proxyDivergenceScore());
    }
}
