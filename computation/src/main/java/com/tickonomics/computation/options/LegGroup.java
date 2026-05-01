package com.tickonomics.computation.options;

import com.tickonomics.cdm.model.CdmOptionSnapshot;
import java.util.List;

public record LegGroup(
    List<CdmOptionSnapshot> components, StrategyType strategyType) {
  public LegGroup {
    components = List.copyOf(components);
  }

  public String getUnderlying() {
    if (components.isEmpty()) {
      return null;
    }
    return components
        .getFirst()
        .underlyingSymbol();
  }

  public int legCount() {
    return components.size();
  }

  public double netPremium() {
    return components
        .stream()
        .mapToDouble(s -> (s.ask() + s.bid()) / 2.0)
        .sum();
  }
}
