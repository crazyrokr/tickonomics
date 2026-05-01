package com.tickonomics.computation.options;

import com.tickonomics.cdm.model.CdmOptionSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LegMatchService {

  private static final double STRIKE_TOLERANCE = 0.01;

  public List<LegGroup> findButterflySpreads(String underlying, List<CdmOptionSnapshot> chain) {
    Map<LocalDate, List<CdmOptionSnapshot>> byExpiry = chain
        .stream()
        .collect(Collectors.groupingBy(CdmOptionSnapshot::expiryDate));

    List<LegGroup> clusters = new ArrayList<>();

    for (var entry : byExpiry.entrySet()) {
      List<CdmOptionSnapshot> sorted = entry
          .getValue()
          .stream()
          .sorted(Comparator.comparing(CdmOptionSnapshot::strike))
          .toList();

      for (int i = 0; i < sorted.size() - 2; i++) {
        for (int j = i + 2; j < sorted.size(); j++) {
          double k1 = sorted
              .get(i)
              .strike()
              .doubleValue();
          double k3 = sorted
              .get(j)
              .strike()
              .doubleValue();
          double targetK2 = (k1 + k3) / 2.0;

          int mid = findStrikeInRange(sorted, i + 1, j - 1, targetK2);
          if (mid != -1) {
            clusters.add(new LegGroup(
                List.of(sorted.get(i), sorted.get(mid), sorted.get(j)),
                StrategyType.CALL_BUTTERFLY));
          }
        }
      }
    }
    return clusters;
  }

  public List<LegGroup> findCondors(String underlying, List<CdmOptionSnapshot> chain) {
    Map<LocalDate, List<CdmOptionSnapshot>> byExpiry = chain
        .stream()
        .collect(Collectors.groupingBy(CdmOptionSnapshot::expiryDate));

    List<LegGroup> clusters = new ArrayList<>();

    for (var entry : byExpiry.entrySet()) {
      List<CdmOptionSnapshot> sorted = entry
          .getValue()
          .stream()
          .sorted(Comparator.comparing(CdmOptionSnapshot::strike))
          .toList();

      for (int i = 0; i < sorted.size() - 3; i++) {
        for (int l = i + 3; l < sorted.size(); l++) {
          for (int j = i + 1; j < l - 1; j++) {
            for (int k = j + 1; k < l; k++) {
              double k1 = sorted
                  .get(i)
                  .strike()
                  .doubleValue();
              double k2 = sorted
                  .get(j)
                  .strike()
                  .doubleValue();
              double k3 = sorted
                  .get(k)
                  .strike()
                  .doubleValue();
              double k4 = sorted
                  .get(l)
                  .strike()
                  .doubleValue();

              double lowerWing = k2 - k1;
              double upperWing = k4 - k3;
              if (Math.abs(lowerWing - upperWing) < STRIKE_TOLERANCE) {
                clusters.add(new LegGroup(
                    List.of(sorted.get(i), sorted.get(j), sorted.get(k), sorted.get(l)),
                    StrategyType.CALL_CONDOR));
              }
            }
          }
        }
      }
    }
    return clusters;
  }

  public List<LegGroup> findVerticalSpreads(String underlying, List<CdmOptionSnapshot> chain) {
    Map<LocalDate, List<CdmOptionSnapshot>> byExpiry = chain
        .stream()
        .collect(Collectors.groupingBy(CdmOptionSnapshot::expiryDate));

    List<LegGroup> clusters = new ArrayList<>();

    for (var entry : byExpiry.entrySet()) {
      List<CdmOptionSnapshot> sorted = entry
          .getValue()
          .stream()
          .sorted(Comparator.comparing(CdmOptionSnapshot::strike))
          .toList();

      for (int i = 0; i < sorted.size() - 1; i++) {
        clusters.add(new LegGroup(List.of(sorted.get(i), sorted.get(i + 1)), StrategyType.BULL_CALL_SPREAD));
      }
    }
    return clusters;
  }

  private int findStrikeInRange(List<CdmOptionSnapshot> sorted, int lo, int hi, double target) {
    for (int i = lo; i <= hi; i++) {
      if (Math.abs(sorted
          .get(i)
          .strike()
          .doubleValue() - target) < STRIKE_TOLERANCE) {
        return i;
      }
    }
    return -1;
  }
}
