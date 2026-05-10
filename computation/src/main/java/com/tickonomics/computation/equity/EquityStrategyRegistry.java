package com.tickonomics.computation.equity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class EquityStrategyRegistry {

  private final Map<String, BaseEquityStrategy> strategies = new LinkedHashMap<>();

  private final UniverseAggregator aggregator;

  public EquityStrategyRegistry() {
    this(new UniverseAggregator());
  }

  public EquityStrategyRegistry(UniverseAggregator aggregator) {
    this.aggregator = aggregator;
    registerDefaults();
  }

  private void registerDefaults() {
    register(new AccumDistStrategy());
    register(new AroonOscillatorStrategy());
    register(new BBBreakoutStrategy());
    register(new BollingerWidthStrategy());
    register(new ChaikinVolStrategy());
    register(new ClusterMeanReversionStrategy(aggregator));
    register(new CoppockCurveStrategy());
    register(new DonchianChannelStrategy());
    register(new EarningsSurpriseStrategy());
    register(new IchimokuCloudStrategy());
    register(new KeltnerChannelStrategy());
    register(new MACDDivergenceStrategy());
    register(new MACrossoverStrategy());
    register(new MeanReversionStrategy());
    register(new MFIInversionStrategy());
    register(new MomentumDecileStrategy(aggregator));
    register(new PairsCointegrationStrategy());
    register(new ParabolicSARStrategy());
    register(new RSIOscillatorStrategy());
    register(new StochasticCrossStrategy());
    register(new SupportResistanceStrategy());
    register(new TRIXReversalStrategy());
    register(new ValueBPStrategy());
    register(new VolumeMomentumStrategy());
    register(new ZigZagFilterStrategy());
  }

  public void register(BaseEquityStrategy strategy) {
    strategies.put(strategy.name(), strategy);
  }

  public Optional<BaseEquityStrategy> get(String name) {
    return Optional.ofNullable(strategies.get(name));
  }

  public Collection<BaseEquityStrategy> all() {
    return Collections.unmodifiableCollection(strategies.values());
  }
}
