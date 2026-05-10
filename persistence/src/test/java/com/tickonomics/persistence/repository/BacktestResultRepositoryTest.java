package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.BacktestResultRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestResultRepositoryTest {

  @Mock
  private NamedParameterJdbcTemplate jdbc;

  private BacktestResultRepository repository;

  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    repository = new BacktestResultRepository(jdbc);
  }

  @Nested
  class Save {

    @Test
    @SuppressWarnings("unchecked")
    void givenRecord_whenSave_thenJdbcUpdateCalledWithKeyHolder() {
      when(jdbc.update(anyString(), any(MapSqlParameterSource.class),
          any(GeneratedKeyHolder.class), any(String[].class)))
          .thenAnswer(invocation -> {
            GeneratedKeyHolder kh = invocation.getArgument(2);
            kh.getKeyList().add(Map.of("id", 1L));
            return 1;
          });

      BacktestResultRecord record = new BacktestResultRecord(
          null, NOW, "{\"strategy\":\"RSI\"}", "[2025-01-01,2025-06-01]",
          1.5, 0.1, 0.6, 2.0, null, null, null, null, null, null, null);

      long id = repository.save(record);

      assertEquals(1L, id);
      verify(jdbc).update(anyString(), any(MapSqlParameterSource.class),
          any(GeneratedKeyHolder.class), any(String[].class));
    }
  }

  @Nested
  class FindById {

    @Test
    @SuppressWarnings("unchecked")
    void givenExistingId_whenFindById_thenReturnPresent() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of(
              new BacktestResultRecord(1L, NOW, "{}", "[]", null, null, null, null, null,
                  null, null, null, null, null, null)));

      Optional<BacktestResultRecord> result = repository.findById(1L);

      assertTrue(result.isPresent());
      assertEquals(1L, result.get().id());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenMissingId_whenFindById_thenReturnEmpty() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      Optional<BacktestResultRecord> result = repository.findById(999L);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class FindByStrategyNameAndTimeBetween {

    @Test
    @SuppressWarnings("unchecked")
    void givenValidRange_whenFindByStrategyName_thenQueryCalled() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<BacktestResultRecord> results = repository.findByStrategyNameAndTimeBetween(
          "RSI", NOW.minusSeconds(86400), NOW);

      verify(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
      assertEquals(0, results.size());
    }
  }

  @Nested
  class FindLatest {

    @Test
    @SuppressWarnings("unchecked")
    void givenLimit_whenFindLatest_thenQueryCalled() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<BacktestResultRecord> results = repository.findLatest(10);

      verify(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
      assertEquals(0, results.size());
    }
  }
}
