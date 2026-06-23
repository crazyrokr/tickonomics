package com.tickonomics.persistence.repository;

import java.util.List;

/**
 * Page envelope for explicit pagination over a range query. {@code total} is the full row count
 * (from a {@code COUNT(*)}); {@code items} is the requested {@code limit}/{@code offset} slice.
 *
 * @param items  the slice of rows for this page
 * @param total  total matching rows across all pages
 * @param limit  page size applied
 * @param offset row offset applied
 * @param <T>    row type
 */
public record PaginatedResponse<T>(List<T> items, long total, int limit, int offset) {

  public int totalPages() {
    return limit <= 0 ? 1 : (int) Math.ceil((double) total / limit);
  }

  public boolean hasNext() {
    return (long) offset + items.size() < total;
  }
}
