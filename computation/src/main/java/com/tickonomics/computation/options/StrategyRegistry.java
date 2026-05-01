package com.tickonomics.computation.options;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class StrategyRegistry {

  private final Map<String, BaseOptionStrategy> strategies = new LinkedHashMap<>();

  public StrategyRegistry() {
    registerDefaults();
  }

  private void registerDefaults() {
    register(new BullCallSpreadStrategy());
    register(new BearPutSpreadStrategy());
    register(new LongStraddleStrategy());
    register(new CallButterflyStrategy());
    register(new PutButterflyStrategy());
    register(new IronCondorStrategy());
    register(new IronButterflyStrategy());
    register(new LongStrangleStrategy());
    register(new RatioSpreadStrategy());
    register(new CalendarSpreadStrategy());
    register(new BoxSpreadStrategy());
    register(new DiagonalSpreadStrategy());
    register(new BackspreadStrategy());
    register(new VerticalPutSpreadStrategy());
    register(new DiagonalCallSpreadStrategy());
    register(new CoveredCallStrategy());
    register(new ProtectivePutStrategy());
    register(new CollarSpreadStrategy());
    register(new MarriedPutStrategy());
    register(new SyntheticLongStrategy());
    register(new BullPutSpreadStrategy());
    register(new BearCallSpreadStrategy());
    register(new CallCondorStrategy());
    register(new PutCondorStrategy());
    register(new RiskReversalStrategy());
    register(new ButterflyBackspreadStrategy());
    register(new ChristmasTreeStrategy());
    register(new SeagullSpreadStrategy());
    register(new StraddleSwapStrategy());
    register(new CalendarStraddleStrategy());
  }

  public void register(BaseOptionStrategy strategy) {
    strategies.put(strategy.name(), strategy);
  }

  public Optional<BaseOptionStrategy> get(String name) {
    return Optional.ofNullable(strategies.get(name));
  }

  public Collection<BaseOptionStrategy> all() {
    return Collections.unmodifiableCollection(strategies.values());
  }
}
