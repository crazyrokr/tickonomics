package com.tickonomics.persistence.repository

import com.tickonomics.persistence.config.QueryLimits
import com.tickonomics.persistence.entity.IdempotentRow
import com.tickonomics.persistence.entity.RateSnapshot
import com.tickonomics.persistence.entity.TickData
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.util.UUID
import java.math.BigDecimal;

class IngestionIdempotencyRepositorySpec extends Specification {

    static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z")
    static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000001")

    NamedParameterJdbcTemplate jdbc = Mock()
    QueryLimits queryLimits = new QueryLimits(10_000)

    @Subject
    TickDataRepository tickRepository = new TickDataRepository(jdbc, queryLimits)

    @Subject
    RateSnapshotRepository rateRepository = new RateSnapshotRepository(jdbc, queryLimits)

    def "given idempotent tick rows, when saveAllIdempotent, then uses ON CONFLICT dedup sql"() {
        given:
            def rows = [
                new IdempotentRow(KEY, new TickData(NOW, "SPY", BigDecimal.valueOf(100.0), 1L, new int[0])),
                new IdempotentRow(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    new TickData(NOW, "QQQ", BigDecimal.valueOf(200.0), 2L, new int[0]))
            ]
            String capturedSql = null

        when:
            tickRepository.saveAllIdempotent(rows)

        then:
            1 * jdbc.batchUpdate(_, _) >> { String sql, batch ->
                capturedSql = sql
                new int[rows.size()]
            }
            capturedSql.contains("idempotency_key")
            capturedSql.contains("ON CONFLICT (time, idempotency_key)")
            capturedSql.contains("DO NOTHING")
    }

    def "given empty tick rows, when saveAllIdempotent, then no jdbc call"() {
        when:
            def result = tickRepository.saveAllIdempotent([])

        then:
            result.length == 0
            0 * jdbc./.*/(*_)
    }

    def "given idempotent rate rows, when saveAllIdempotent, then uses ON CONFLICT dedup sql"() {
        given:
            def rows = [
                new IdempotentRow(KEY, new RateSnapshot(NOW, "EFFR", 5.25, "FRED", null, null))
            ]
            String capturedSql = null

        when:
            rateRepository.saveAllIdempotent(rows)

        then:
            1 * jdbc.batchUpdate(_, _) >> { String sql, batch ->
                capturedSql = sql
                new int[rows.size()]
            }
            capturedSql.contains("idempotency_key")
            capturedSql.contains("ON CONFLICT (time, idempotency_key)")
            capturedSql.contains("DO NOTHING")
    }

    def "given empty rate rows, when saveAllIdempotent, then no jdbc call"() {
        when:
            def result = rateRepository.saveAllIdempotent([])

        then:
            result.length == 0
            0 * jdbc./.*/(*_)
    }
}
