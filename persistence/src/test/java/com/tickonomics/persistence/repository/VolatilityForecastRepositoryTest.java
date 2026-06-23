package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.config.QueryLimits;
import com.tickonomics.persistence.entity.VolatilityForecast;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VolatilityForecastRepositoryTest {

  @Mock
  private NamedParameterJdbcTemplate jdbc;

  private final QueryLimits queryLimits = new QueryLimits(10_000);

  private VolatilityForecastRepository repository;

  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    repository = new VolatilityForecastRepository(jdbc, queryLimits);
  }

  private VolatilityForecast buildForecast() {
    return new VolatilityForecast(
        NOW, "SPY", "garch", 5, 0.18, null, 0.02, 500,
        "{\"omega\":0.1,\"alpha\":0.2,\"beta\":0.7}", null, NOW);
  }

  @Nested
  class Save {

    @Test
    void givenForecast_whenSave_thenJdbcUpdateCalled() {
      when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

      repository.save(buildForecast());

      verify(jdbc).update(anyString(), any(MapSqlParameterSource.class));
    }
  }

  @Nested
  class FindBySymbolAndTimeBetween {

    @Test
    @SuppressWarnings("unchecked")
    void givenValidRange_whenFindBySymbol_thenQueryCalled() {
      when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<VolatilityForecast> results = repository.findBySymbolAndTimeBetween(
          "SPY", NOW.minusSeconds(86400), NOW);

      verify(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
      assertEquals(0, results.size());
    }
  }

  @Nested
  class FindLatestBySymbol {

    @Test
    @SuppressWarnings("unchecked")
    void givenSymbol_whenFindLatest_thenQueryCalled() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<VolatilityForecast> results = repository.findLatestBySymbol("SPY", 10);

      verify(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
      assertEquals(0, results.size());
    }
  }

  @Nested
  class UpdateRealizedVol {

    @Test
    void givenForecastWithNullRealized_whenUpdateRealizedVol_thenUpdateCalled() {
      when(jdbc.update(anyString(), any(Map.class))).thenReturn(1);

      repository.updateRealizedVol(NOW, "SPY", 5, 0.20);

      verify(jdbc).update(anyString(), any(Map.class));
    }
  }
}
