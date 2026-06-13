package com.tickonomics.computation.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.tickonomics.persistence.entity.MarkovStopCalibration;
import com.tickonomics.persistence.repository.MarkovStopCalibrationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkovStopHandlerTest {

  @Mock
  private MarkovStopCalibrationRepository repository;

  private MarkovStopHandler handler;

  @BeforeEach
  void setUp() {
    handler = new MarkovStopHandler(repository);
  }

  @Nested
  class StopsFor {

    @Test
    void givenCalibrationExists_whenStopsFor_thenPresentWithMappedFields() {
      when(repository.findLatestBySymbol("SPY")).thenReturn(Optional.of(
          new MarkovStopCalibration(1L, "SPY", 4.5, 9.0, 0.01, 0.3, true, 200, Instant.now())));

      MarkovStopHandler.CalibratedStops stops = handler.stopsFor("SPY").orElseThrow();

      assertEquals("SPY", stops.symbol());
      assertEquals(4.5, stops.stopLossPct());
      assertEquals(9.0, stops.takeProfitPct());
      assertTrue(stops.converged());
    }

    @Test
    void givenNoCalibration_whenStopsFor_thenEmpty() {
      when(repository.findLatestBySymbol("QQQ")).thenReturn(Optional.empty());

      assertTrue(handler.stopsFor("QQQ").isEmpty());
    }
  }
}
