package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.TickData;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class TickDataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public TickDataRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(TickData tick) {
        jdbc.update(
                "INSERT INTO tick_data (time, symbol, price, volume, conditions) VALUES (:time, :symbol, :price, :volume, :conditions)",
                toParams(tick));
    }

    public void saveAll(List<TickData> ticks) {
        jdbc.batchUpdate(
                "INSERT INTO tick_data (time, symbol, price, volume, conditions) VALUES (:time, :symbol, :price, :volume, :conditions)",
                SqlParameterSourceUtils.createBatch(ticks.stream().map(this::toParams).toArray(MapSqlParameterSource[]::new)));
    }

    public List<TickData> findBySymbolAndTimeBetween(String symbol, Instant from, Instant to) {
        return jdbc.query(
                "SELECT time, symbol, price, volume, conditions FROM tick_data WHERE symbol = :symbol AND time BETWEEN :from AND :to ORDER BY time",
                Map.of("symbol", symbol, "from", from, "to", to),
                (rs, rowNum) -> new TickData(
                        rs.getTimestamp("time").toInstant(),
                        rs.getString("symbol"),
                        rs.getDouble("price"),
                        rs.getLong("volume"),
                        (int[]) rs.getArray("conditions").getArray()));
    }

    public List<TickData> findLatestBySymbol(String symbol, int limit) {
        return jdbc.query(
                "SELECT time, symbol, price, volume, conditions FROM tick_data WHERE symbol = :symbol ORDER BY time DESC LIMIT :limit",
                Map.of("symbol", symbol, "limit", limit),
                (rs, rowNum) -> new TickData(
                        rs.getTimestamp("time").toInstant(),
                        rs.getString("symbol"),
                        rs.getDouble("price"),
                        rs.getLong("volume"),
                        (int[]) rs.getArray("conditions").getArray()));
    }

    private MapSqlParameterSource toParams(TickData tick) {
        return new MapSqlParameterSource()
                .addValue("time", tick.time())
                .addValue("symbol", tick.symbol())
                .addValue("price", tick.price())
                .addValue("volume", tick.volume())
                .addValue("conditions", tick.conditions() != null ? tick.conditions() : new int[]{});
    }
}
