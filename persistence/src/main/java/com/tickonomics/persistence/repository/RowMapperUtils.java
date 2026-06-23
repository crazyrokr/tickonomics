package com.tickonomics.persistence.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Null-safe column extraction for {@link ResultSet}. JDBC's {@code getDouble}/{@code getInt}/
 * {@code getBoolean} return {@code 0.0}/{@code 0}/{@code false} for SQL {@code NULL}, silently
 * corrupting nullable metrics (correlations, p-values, anomaly scores). These helpers preserve
 * {@code null} so nullable columns round-trip as {@code null} into boxed entity fields.
 */
public final class RowMapperUtils {

  private RowMapperUtils() {
    throw new UnsupportedOperationException("utility class");
  }

  public static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column) != null ? rs.getDouble(column) : null;
  }

  public static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column) != null ? rs.getInt(column) : null;
  }

  public static Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column) != null ? rs.getBoolean(column) : null;
  }
}
