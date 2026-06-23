package com.tickonomics.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaginatedResponseTest {

  @Nested
  class TotalPages {

    @Test
    void givenTotalDivisibleByLimit_whenTotalPages_thenExactPageCount() {
      PaginatedResponse<String> page = new PaginatedResponse<>(List.of("a"), 40, 10, 0);
      assertEquals(4, page.totalPages());
    }

    @Test
    void givenRemainder_whenTotalPages_thenRoundsUp() {
      PaginatedResponse<String> page = new PaginatedResponse<>(List.of("a"), 42, 10, 0);
      assertEquals(5, page.totalPages());
    }

    @Test
    void givenNonPositiveLimit_whenTotalPages_thenOne() {
      PaginatedResponse<String> page = new PaginatedResponse<>(List.of("a"), 42, 0, 0);
      assertEquals(1, page.totalPages());
    }
  }

  @Nested
  class HasNext {

    @Test
    void givenMorePagesRemain_whenHasNext_thenTrue() {
      PaginatedResponse<String> page = new PaginatedResponse<>(List.of("a", "b"), 10, 2, 0);
      assertTrue(page.hasNext());
    }

    @Test
    void givenLastPage_whenHasNext_thenFalse() {
      PaginatedResponse<String> page = new PaginatedResponse<>(List.of("a"), 10, 10, 9);
      assertFalse(page.hasNext());
    }
  }
}
