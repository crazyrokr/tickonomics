package com.tickonomics.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class BoundedRangeQueryTest {

  private static final Logger log = LoggerFactory.getLogger(BoundedRangeQueryTest.class);

  @Mock
  private NamedParameterJdbcTemplate jdbc;

  @SuppressWarnings("unchecked")
  private final RowMapper<String> mapper = (rs, rowNum) -> "row";

  @Test
  @SuppressWarnings("unchecked")
  void givenNoOffset_whenExecute_thenAppendsLimitOnly() {
    // Given a query with limit but no offset
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of("a"));
    var sqlCaptor = ArgumentCaptor.forClass(String.class);

    // When executed
    List<String> results =
        BoundedRangeQuery.execute(jdbc, "SELECT x", new MapSqlParameterSource(), mapper, 100, null, log, "q");

    // Then the SQL appends only LIMIT :limit (no OFFSET)
    verify(jdbc).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
    assertTrue(sqlCaptor.getValue().endsWith(" LIMIT :limit"), sqlCaptor.getValue());
    assertEquals(List.of("a"), results);
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenPositiveOffset_whenExecute_thenAppendsLimitAndOffset() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());
    var sqlCaptor = ArgumentCaptor.forClass(String.class);

    BoundedRangeQuery.execute(jdbc, "SELECT x", new MapSqlParameterSource(), mapper, 50, 100, log, "q");

    verify(jdbc).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
    assertTrue(sqlCaptor.getValue().endsWith(" LIMIT :limit OFFSET :offset"), sqlCaptor.getValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenZeroOffset_whenExecute_thenAppendsLimitOnly() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());
    var sqlCaptor = ArgumentCaptor.forClass(String.class);

    BoundedRangeQuery.execute(jdbc, "SELECT x", new MapSqlParameterSource(), mapper, 50, 0, log, "q");

    verify(jdbc).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
    assertTrue(sqlCaptor.getValue().endsWith(" LIMIT :limit"), sqlCaptor.getValue());
  }
}
