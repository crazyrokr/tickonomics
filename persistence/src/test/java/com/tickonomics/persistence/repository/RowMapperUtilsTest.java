package com.tickonomics.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RowMapperUtilsTest {

  @Nested
  class GetNullableDouble {

    @Test
    void givenNonNullColumn_whenGetNullableDouble_thenReturnsValue() throws Exception {
      // Given a column holding a non-null value
      ResultSet rs = mock(ResultSet.class);
      when(rs.getObject("correlation")).thenReturn(0.87);
      when(rs.getDouble("correlation")).thenReturn(0.87);

      // When extracted nullably
      Double value = RowMapperUtils.getNullableDouble(rs, "correlation");

      // Then the value is preserved
      assertEquals(0.87, value);
    }

    @Test
    void givenNullColumn_whenGetNullableDouble_thenReturnsNullNotZero() throws Exception {
      // Given a SQL NULL column (the false-positive case: getDouble would yield 0.0)
      ResultSet rs = mock(ResultSet.class);
      when(rs.getObject("correlation")).thenReturn(null);

      // When extracted nullably
      Double value = RowMapperUtils.getNullableDouble(rs, "correlation");

      // Then NULL round-trips as null, not 0.0
      assertNull(value);
    }
  }

  @Nested
  class GetNullableInt {

    @Test
    void givenNonNullColumn_whenGetNullableInt_thenReturnsValue() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getObject("sample_size")).thenReturn(250);
      when(rs.getInt("sample_size")).thenReturn(250);

      assertEquals(250, RowMapperUtils.getNullableInt(rs, "sample_size"));
    }

    @Test
    void givenNullColumn_whenGetNullableInt_thenReturnsNullNotZero() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getObject("sample_size")).thenReturn(null);

      assertNull(RowMapperUtils.getNullableInt(rs, "sample_size"));
    }
  }

  @Nested
  class GetNullableBoolean {

    @Test
    void givenNonNullColumn_whenGetNullableBoolean_thenReturnsValue() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getObject("is_suspect_anomaly")).thenReturn(true);
      when(rs.getBoolean("is_suspect_anomaly")).thenReturn(true);

      assertTrue(RowMapperUtils.getNullableBoolean(rs, "is_suspect_anomaly"));
    }

    @Test
    void givenNullColumn_whenGetNullableBoolean_thenReturnsNullNotFalse() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getObject("is_suspect_anomaly")).thenReturn(null);

      assertNull(RowMapperUtils.getNullableBoolean(rs, "is_suspect_anomaly"));
    }
  }
}
