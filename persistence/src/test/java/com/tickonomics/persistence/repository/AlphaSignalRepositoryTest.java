package com.tickonomics.persistence.repository;

import com.tickonomics.persistence.entity.AlphaSignalRecord;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlphaSignalRepositoryTest {

  @Mock
  private NamedParameterJdbcTemplate jdbc;

  private AlphaSignalRepository repository;

  private static final UUID STRATEGY_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    repository = new AlphaSignalRepository(jdbc);
  }

  @Nested
  class Save {

    @Test
    void givenSignal_whenSave_thenJdbcUpdateCalled() {
      AlphaSignalRecord signal = new AlphaSignalRecord(
          NOW, STRATEGY_ID, "SPY", "LONG", 0.8, 0.9, 1.5, "{}");
      when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

      repository.save(signal);

      verify(jdbc).update(anyString(), any(MapSqlParameterSource.class));
    }
  }

  @Nested
  class SaveAll {

    @Test
    @SuppressWarnings("unchecked")
    void givenMultipleSignals_whenSaveAll_thenBatchUpdateCalled() {
      List<AlphaSignalRecord> signals = List.of(
          new AlphaSignalRecord(NOW, STRATEGY_ID, "SPY", "LONG", 0.8, 0.9, null, null),
          new AlphaSignalRecord(NOW, STRATEGY_ID, "QQQ", "SHORT", 0.7, 0.6, null, null));

      repository.saveAll(signals);

      verify(jdbc).batchUpdate(anyString(), any(SqlParameterSource[].class));
    }
  }

  @Nested
  class FindByStrategyIdAndTimeBetween {

    @Test
    @SuppressWarnings("unchecked")
    void givenValidRange_whenFindByStrategyId_thenQueryCalled() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<AlphaSignalRecord> results = repository.findByStrategyIdAndTimeBetween(
          STRATEGY_ID, NOW.minusSeconds(86400), NOW);

      verify(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
      assertEquals(0, results.size());
    }
  }

  @Nested
  class FindBySymbolAndTimeBetween {

    @Test
    @SuppressWarnings("unchecked")
    void givenValidRange_whenFindBySymbol_thenQueryCalled() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<AlphaSignalRecord> results = repository.findBySymbolAndTimeBetween(
          "SPY", NOW.minusSeconds(86400), NOW);

      verify(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
      assertEquals(0, results.size());
    }
  }

  @Nested
  class FindLatestByStrategyId {

    @Test
    @SuppressWarnings("unchecked")
    void givenStrategyId_whenFindLatest_thenQueryCalled() {
      when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<AlphaSignalRecord> results = repository.findLatestByStrategyId(STRATEGY_ID, 10);

      verify(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
      assertEquals(0, results.size());
    }
  }
}
