package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.IngestionDlqEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class IngestionDlqRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IngestionDlqRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(IngestionDlqEntry entry) {
        jdbc.update(
                "INSERT INTO ingestion_dlq (source, payload, error_message, idempotency_key) VALUES (:source, :payload::jsonb, :errorMessage, :idempotencyKey)",
                toParams(entry));
    }

    public List<IngestionDlqEntry> findUnreplayed(int limit) {
        return jdbc.query(
                "SELECT created_at, source, payload, error_message, idempotency_key FROM ingestion_dlq WHERE NOT replayed ORDER BY created_at LIMIT :limit",
                Map.of("limit", limit),
                (rs, rowNum) -> new IngestionDlqEntry(
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("source"),
                        rs.getString("payload"),
                        rs.getString("error_message"),
                        rs.getObject("idempotency_key") != null ? java.util.UUID.fromString(rs.getObject("idempotency_key").toString()) : null));
    }

    public void markReplayed(long id) {
        jdbc.update("UPDATE ingestion_dlq SET replayed = TRUE WHERE id = :id", Map.of("id", id));
    }

    private MapSqlParameterSource toParams(IngestionDlqEntry entry) {
        return new MapSqlParameterSource()
                .addValue("source", entry.source())
                .addValue("payload", entry.payload())
                .addValue("errorMessage", entry.errorMessage())
                .addValue("idempotencyKey", entry.idempotencyKey());
    }
}
