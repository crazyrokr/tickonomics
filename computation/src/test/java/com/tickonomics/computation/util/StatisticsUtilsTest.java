package com.tickonomics.computation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatisticsUtilsTest {

  // Classic dataset: mean = 5, population variance = 4, population stddev = 2.
  private static final List<Double> DATA = List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);

  @Nested
  class Mean {

    @Test
    void givenKnownDataset_whenMean_thenArithmeticMean() {
      // Given a dataset with mean 5
      // When computing the mean
      // Then the arithmetic mean is returned
      assertEquals(5.0, StatisticsUtils.mean(DATA), 1e-9);
    }

    @Test
    void givenEmptyOrNull_whenMean_thenZero() {
      assertEquals(0.0, StatisticsUtils.mean(List.of()), 1e-9);
      assertEquals(0.0, StatisticsUtils.mean((List<Double>) null), 1e-9);
    }

    @Test
    void givenDoubleArray_whenMean_thenArithmeticMean() {
      assertEquals(5.0, StatisticsUtils.mean(new double[] {2, 4, 4, 4, 5, 5, 7, 9}), 1e-9);
      assertEquals(0.0, StatisticsUtils.mean(new double[] {}), 1e-9);
    }
  }

  @Nested
  class StdDev {

    @Test
    void givenKnownDataset_whenPopulationStdDev_thenTwo() {
      // Given the dataset has population stddev 2
      // When computing population stddev
      // Then 2 is returned
      assertEquals(2.0, StatisticsUtils.populationStdDev(DATA), 1e-9);
      assertEquals(2.0, StatisticsUtils.populationStdDev(DATA, 5.0), 1e-9);
    }

    @Test
    void givenUniformValues_whenPopulationStdDev_thenZero() {
      assertEquals(0.0, StatisticsUtils.populationStdDev(List.of(5.0, 5.0, 5.0)), 1e-9);
    }

    @Test
    void givenSampleVariance_whenComparedToPopulation_thenSampleIsLarger() {
      // Given the dataset
      // When computing sample vs population variance
      // Then sample variance (n-1) exceeds population variance (n)
      double population = StatisticsUtils.populationVariance(DATA, 5.0);
      double sample = StatisticsUtils.sampleVariance(DATA, 5.0);
      assertEquals(4.0, population, 1e-9);
      assertEquals(32.0 / 7.0, sample, 1e-9);
    }

    @Test
    void givenDoubleArray_whenPopulationStdDev_thenTwo() {
      assertEquals(2.0, StatisticsUtils.populationStdDev(
          new double[] {2, 4, 4, 4, 5, 5, 7, 9}, 5.0), 1e-9);
    }
  }

  @Nested
  class Sharpe {

    @Test
    void givenKnownDataset_whenSharpe_thenMeanOverStd() {
      // Given mean=5 and population std=2
      // When computing Sharpe against a zero risk-free rate
      // Then 5/2 = 2.5
      assertEquals(2.5, StatisticsUtils.sharpe(DATA, 0.0), 1e-9);
    }

    @Test
    void givenDegenerateStddev_whenSharpe_thenZero() {
      // Given a constant series (stddev = 0)
      // When computing Sharpe
      // Then 0 is returned rather than dividing by zero
      assertEquals(0.0, StatisticsUtils.sharpe(List.of(5.0, 5.0, 5.0), 0.0), 1e-9);
      assertEquals(0.0, StatisticsUtils.sharpe(List.of(), 0.0), 1e-9);
    }
  }
}
