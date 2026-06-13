package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.DailyClose;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OhlcvDailyRepository {

  private static final String CLOSES_BETWEEN_SQL =
      "SELECT day, (candle).close AS close FROM ohlcv_1d "
          + "WHERE symbol = :symbol AND day >= :from AND day < :toExclusive ORDER BY day";

  private final NamedParameterJdbcTemplate jdbc;

  public OhlcvDailyRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DailyClose> findClosesBetween(String symbol, LocalDate from, LocalDate toExclusive) {
    return jdbc.query(
        CLOSES_BETWEEN_SQL,
        Map.of(
            "symbol", symbol,
            "from", toTimestamp(from),
            "toExclusive", toTimestamp(toExclusive)),
        (rs, rowNum) -> new DailyClose(toLocalDate(rs.getTimestamp("day")), rs.getDouble("close")));
  }

  public Optional<Double> findClose(String symbol, LocalDate day) {
    return findClosesBetween(symbol, day, day.plusDays(1)).stream()
        .map(DailyClose::close)
        .findFirst();
  }

  private static Timestamp toTimestamp(LocalDate day) {
    return Timestamp.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
  }

  private static LocalDate toLocalDate(Timestamp timestamp) {
    return timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
  }
}
