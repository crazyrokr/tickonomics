---
  Java Best Practice Review — Tickonomics

  Scope: 7 project modules (api-contracts, app, cdm, computation, ingestion, persistence, web) — 161 main-source Java files. Third-party native-libs/ excluded.
  
  Result: 63 issues found (9 🔴 High · 34 🟡 Medium · 20 🟢 Low)

  ---
  🔴 HIGH Severity
  
  Issue 1 — Mutable internal array exposed via accessor (CorrelationEngine.CorrelationResult.series)

  The series() accessor returns a direct reference to the internal double[], allowing external callers to mutate the object's state.
  // ✅ Fix
  public double[] series() {
      return series.clone();
  }

  Issue 2 — Record exposes mutable array without defensive copy (BBandsResult)

  The upper(), middle(), lower() accessors return the internal double[] directly, allowing callers to corrupt record state.
  // ✅ Fix
  public record BBandsResult(double[] upper, double[] middle, double[] lower, int begIdx, int nbElement) {
    public BBandsResult {
      upper = upper.clone();
      middle = middle.clone();
      lower = lower.clone();
    }
  }

  Issue 3 — Thread-unsafe public setters on Spring singleton (SignalGenerator fields)

  setThresholds, setTransactionCostBps, setCooldownMs allow external callers to mutate a Spring-managed singleton's configuration without synchronization — a race
  condition.
  // ✅ Fix
  private final SignalGeneratorConfig config;
  
  public SignalGenerator(NormalizationService normalizationService,
                         SignalGeneratorConfig config) {
      this.normalizationService = normalizationService;
      this.config = config;
  }

  Issue 4 — == comparing Long wrapper values (VirtualPortfolio.closePosition)
  
  position.id() returns Long; positionId is long. The == comparison autoboxes positionId to Long, using reference identity instead of value equality for values
  outside the −128..127 cache.
  // ✅ Fix
  .filter(p -> p.id().longValue() == positionId)

  Issue 5 — Duplicated 16-line row-mapper lambda ×3 (BacktestResultRepository)

  The same ResultSet → BacktestResultRecord lambda is copy-pasted in findById, findByStrategyNameAndTimeBetween, and findLatest — ~30 lines of duplication.
  // ✅ Fix: extract a reusable row mapper
  private final RowMapper<BacktestResultRecord> rowMapper = (rs, rowNum) -> new BacktestResultRecord(
      rs.getLong("id"),
      rs.getTimestamp("run_at").toInstant(),
      rs.getString("strategy_config"),
      // ... remaining fields
  );  
  // Then use: jdbc.query(sql, params, rowMapper);
  
  Issue 6 — Duplicated row-mapper lambda ×3 (AlphaSignalRepository)

  Same anti-pattern as above in findByStrategyIdAndTimeBetween, findBySymbolAndTimeBetween, findLatestByStrategyId.
  // ✅ Fix: extract a reusable RowMapper<AlphaSignalRecord> field
  
  Issue 7 — Duplicated row-mapper lambda ×3 (SignalLogRepository)

  Same anti-pattern in findBySymbolAndTimeBetween, findByCreatedAtBetween, findLatestByStatus — ~27 lines repeated.
  // ✅ Fix: extract a reusable RowMapper<SignalLog> field
  
  Issue 8 — Mutable array in record without defensive copy (CdmTick.conditions)

  The int[] conditions field is a mutable array passed directly into the record; external code can corrupt record state.
  // ✅ Fix
  public record CdmTick(Instant time, String symbol, double price, long volume, int[] conditions) {
    public CdmTick {
      conditions = conditions != null ? conditions.clone() : null;
    } 
    @Override
    public int[] conditions() {
      return conditions != null ? conditions.clone() : null;
    } 
  } 
  
  Issue 9 — Mutable array in record without defensive copy (PolygonTick.conditions)

  Identical to Issue 8 in PolygonTick.
  // ✅ Fix — same pattern: clone in compact constructor and override accessor
  
  ---
  🟡 MEDIUM Severity

  Issue 10 — String concatenation inside loop (BacktestEngine.runEquityBacktest)

  input.put("return_" + i, ...) creates a new String on every iteration via concatenation.
  // ✅ Fix
  StringBuilder keyBuilder = new StringBuilder("return_");
  for (int i = 0; i < dailyReturns.size(); i++) {
      keyBuilder.setLength(7);
      keyBuilder.append(i);
      input.put(keyBuilder.toString(), dailyReturns.get(i));
  }
  
  Issue 11 — Manual JSON via string concatenation (BacktestEngine.persistResult)

  Fragile JSON construction with + — should use a text block or Jackson.
  // ✅ Fix
  String metadata = """
      {"strategy":"%s"}
      """.formatted(result.strategyName());

  Issue 12 — != on double with potential NaN (DelayDExecutor.compareDelays)

  delay1.sharpeRatio() != 0 is fragile; should use Double.compare() or epsilon check.
  // ✅ Fix
  if (Double.compare(delay1.sharpeRatio(), 0.0) != 0
      && Math.abs(delay0.sharpeRatio() / delay1.sharpeRatio()) > FRAGILITY_THRESHOLD) {

  Issue 13 — Manual hex conversion instead of HexFormat (ReproducibilityService.computeDatasetHash)

  String.format("%02x", b) in a loop when HexFormat.of().formatHex(hash) does the same in one call.
  // ✅ Fix
  return HexFormat.of().formatHex(hash);

  Issue 14 — StringBuilder-based manual JSON serialization (ReproducibilityService.serializeHyperparams)

  Error-prone manual JSON construction — no escaping, no nesting support.
  // ✅ Fix — use Jackson ObjectMapper
  return new ObjectMapper().writeValueAsString(hyperparams);

  Issue 15 — Substring-based day key extraction (HistoricalDataReplay.extractDailyLastPrices)

  toString().substring(0, 10) on an Instant is locale/offset dependent.
  // ✅ Fix
  String dayKey = tick.time().atZone(ZoneOffset.UTC).toLocalDate().toString();

  Issue 16 — Manual new instantiation of 25 strategies (EquityStrategyRegistry)

  Prevents Spring DI and makes the registry untestable in isolation.
  // ✅ Fix — inject List<BaseEquityStrategy> via constructor
  public EquityStrategyRegistry(List<BaseEquityStrategy> strategyBeans, ...) {
      for (BaseEquityStrategy s : strategyBeans) {
          strategies.put(s.name(), s);
      }   
  }   
  
  Issue 17 — Manual new instantiation of 30 option strategies (StrategyRegistry)

  Same anti-pattern as Issue 16.

  Issue 18 — Math.random() instead of ThreadLocalRandom (BayesianWeightOptimizer.proposeUpdate)

  Math.random() contends on a shared Random instance; ThreadLocalRandom avoids synchronization overhead in optimization loops.
  // ✅ Fix
  double perturbation = (ThreadLocalRandom.current().nextDouble() - 0.5) * profile.learningRate();

  Issue 19 — Silent exception swallowing (KpiProcessor.getRecentRates)

  Catches all exceptions silently and returns empty list, masking data-access failures.
  // ✅ Fix — log the failure
  } catch (Exception e) {
      log.warn("Failed to fetch rates for {}: {}", rateType, e.getMessage());
      return List.of();
  }

  Issue 20 — RootAllocator never closed (ArrowIpcTransport)

  Arrow allocators hold off-heap memory and must be closed in long-running services.
  // ✅ Fix — implement AutoCloseable or inject a shared allocator bean

  Issue 21 — Unnecessary autoboxing of Double from Map.get() (MarketStressSimulator)

  // ✅ Fix
  return profile.conditionThresholds().getOrDefault("volatility_zscore", 1.0);

  Issue 22 — Invalid SLF4J format string {:.2f} (RegimeDetector)
  
  SLF4J only supports {} — {:.2f} is Log4j2-style and produces incorrect output.
  // ✅ Fix
  log.debug("Detected regime {} with confidence {}", regime, String.format("%.2f", confidence));

  Issue 23 — Operator precedence bug (RegimeDetector.detectByVolatilityPercentile)

  round(latestVol - meanVol) / (stdVol > 0 ? stdVol : 1) divides the rounded difference — parentheses are misleading.
  // ✅ Fix
  round((latestVol - meanVol) / (stdVol > 0 ? stdVol : 1))
  
  Issue 24 — JSON strings built with concatenation instead of text blocks (DefaultPolygonWsClient.sendSubscribe)

  // ✅ Fix
  private void sendSubscribe(String symbol) {
      sendMessage("""
          {"action":"subscribe","params":"T.%s"}
          """.formatted(symbol));
  }

  Issue 25 — ObjectMapper instantiated per instance (FileOverflowBuffer)

  ObjectMapper is heavyweight and thread-safe — should be injected.
  // ✅ Fix — add ObjectMapper as a constructor parameter

  Issue 26 — Mutually exclusive if-return branches (ToxicityMonitor.classifyToxicity)

  // ✅ Fix
  return score > 70 ? ToxicityClassification.HARMFUL
       : score < 30 ? ToxicityClassification.BENEFICIAL
       : ToxicityClassification.NEUTRAL;

  Issue 27–30 — @Value on fields instead of constructor params (FredClient, NyFedClient, EconomicCalendarClient, OptionsDataClient)

  Field injection prevents immutability and makes unit testing harder. Move @Value annotations to constructor parameters.
  
  Issue 31 — Failed batch re-add risks duplicate processing (TimescaleDbWriter.flushTicks)

  On flush failure, batch.addAll(batch) puts drained items back; verify idempotency guard keys are set before add-to-queue, not before flush.

  Issue 32 — toFile().length() instead of Files.size() (FileOverflowBuffer.hasData)

  // ✅ Fix
  return Files.exists(overflowPath) && Files.size(overflowPath) > 0;

  Issue 33 — List<Double> parameter causes autoboxing overhead (KernelAggregator.computeRealizedVariance)

  Accepting List<Double> with get(i) in a tight numeric loop causes unneeded auto-unboxing.
  // ✅ Fix — accept double[] instead

  Issue 34 — @Bulkhead at wrong level (FredClient.pollAllSeries)

  A single slow series can exhaust the bulkhead for all others. Move @Bulkhead to the per-series method.

  Issue 35–36 — Dead Flyway configuration (TimescaleDbConfig.flywayMigrationStrategy)

  Flyway.configure().baselineOnMigrate(true).load() is called but the result is discarded — baselineOnMigrate has no effect.
  // ✅ Fix
  return flyway -> {
      Flyway.configure()
          .dataSource(flyway.getConfiguration().getDataSource())
          .baselineOnMigrate(true)
          .load()
          .migrate();
  };

  Issue 37 — Missing compact constructor validation (ProxyDivergenceEvent)

  No validation at all — allows null detectedAt and unchecked divergenceScore.

  Issue 38 — 13-parameter method instead of domain object (SignalQualityReportRepository.save)
  
  // ✅ Fix — create a SignalQualityReport record and accept it

  Issue 39 — Redundant bean (TimescaleDbConfig.namedParameterJdbcTemplate)

  Spring Boot auto-configures NamedParameterJdbcTemplate — the explicit bean is unnecessary.

  Issue 40 — Missing Double.isFinite for Greeks (CdmOptionSnapshot)

  Fields delta, gamma, theta, vega, rho, impliedVol, ttmYears, bid, ask are not validated for NaN/Infinity — unlike CdmBondSnapshot.

  Issue 41 — Missing Double.isFinite for rateDelta/rateGamma (CdmBondSnapshot)
  
  Inconsistent validation — yieldValue, dv01, convexity, duration are checked but rateDelta and rateGamma are not.

  ---
  🟢 LOW Severity

  ┌─────┬─────────────────────────────────────────────┬───────────────┬────────────────────────────────────────────────────────────────────────┐
  │  #  │                  Location                   │   Category    │                                 Issue                                  │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 42  │ KpiProcessor.computeVolatilityRegime()      │ Conditionals  │ if-else chain for mutually exclusive regime could be switch expression │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 43  │ CorrelationEngine.getLatestValues()         │ Memory        │ Empty catch blocks silently swallowing exceptions                      │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 44  │ VirtualPortfolio.findOpenBySymbol()         │ Code Style    │ Returns null instead of Optional                                       │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 45  │ DelayDExecutor.executeWithDelay()           │ Loops         │ Second pass for variance; single-pass Welford would be better          │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 46  │ DefaultPolygonWsClient.subscribedSymbols    │ Concurrency   │ volatile WebSocketSession has check-then-act race                      │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 47  │ PolygonWsConfig.polygonWsClient()           │ Concurrency   │ SimpleAsyncTaskExecutor instead of virtual threads                     │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 48  │ DeRoundingFilter.computeTimeWeightedPrice() │ Loops         │ Minor: ticks.size() - 1 could be hoisted                               │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 49  │ FredClient.FRED_DATE_FMT                    │ Boilerplate   │ ofPattern("yyyy-MM-dd") duplicates ISO_LOCAL_DATE                      │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 50  │ NyFedClient.NYFED_DATE_FMT                  │ Boilerplate   │ Same as above                                                          │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 51  │ IdempotencyGuard.buildKey()                 │ Strings       │ + concatenation in non-loop context (acceptable)                       │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 52  │ DefaultPolygonWsClient.parseTick()          │ Arrays        │ Manual loop could use IntStream for array population                   │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 53  │ TimescaleDbWriter.batchSize                 │ Encapsulation │ Package-private field should be private                                │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 54  │ TimescaleDbWriter.flushIntervalMs           │ Boilerplate   │ Unused field — scheduling is via @Scheduled                            │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 55  │ DefaultPolygonWsClient.PolygonWsHandler     │ OOP           │ Non-static inner class captures outer this                             │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 56  │ AlphaSignalRecord compact constructor       │ Records       │ IllegalArgumentException vs NullPointerException inconsistency         │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 57  │ SignalQualityReportRepository queries       │ Encapsulation │ Returns Map<String, Object> instead of typed record                    │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 58  │ BacktestResultRepository LIKE query         │ Conditionals  │ LIKE '%name%' on JSONB is fragile; use ->>'name'                       │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 59  │ VirtualPortfolioPositionRepository.mapRow() │ OOP           │ Unnecessary indirection through private mapRow method                  │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 60  │ VirtualPortfolioTradeRepository.rowMapper() │ Encapsulation │ rowMapper() creates new lambda each call; should be a field            │
  ├─────┼─────────────────────────────────────────────┼───────────────┼────────────────────────────────────────────────────────────────────────┤
  │ 61  │ DemoController.getPortfolioSummary()        │ Collections   │ Mutable LinkedHashMap where Map.ofEntries() would do                   │
  └─────┴─────────────────────────────────────────────┴───────────────┴────────────────────────────────────────────────────────────────────────┘

  ---
  Summary Table

  ┌─────┬───────────────────────────────────────────────┬─────────────────┬─────────────────────────────────────────────────┬──────────┐
  │  #  │                  Location                     │    Category     │                     Issue                       │ Severity │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 1   │ CorrelationEngine.CorrelationResult.series    │ Encapsulation   │ Mutable internal array exposed                  │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 2   │ BBandsResult accessors                        │ Encapsulation   │ Mutable array in record without defensive copy  │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 3   │ SignalGenerator fields                        │ Encapsulation   │ Thread-unsafe public setters on singleton       │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 4   │ VirtualPortfolio.closePosition()              │ Wrapper Classes │ == comparing Long wrapper values                │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 5   │ BacktestResultRepository                      │ OOP Design      │ Duplicated 16-line row mapper ×3                │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 6   │ AlphaSignalRepository                         │ OOP Design      │ Duplicated row mapper ×3                        │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 7   │ SignalLogRepository                           │ OOP Design      │ Duplicated row mapper ×3                        │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 8   │ CdmTick.conditions                            │ Encapsulation   │ Mutable array in record                         │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 9   │ PolygonTick.conditions                        │ Encapsulation   │ Mutable array in record                         │ 🔴       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 10  │ BacktestEngine.runEquityBacktest()            │ Strings         │ Concatenation in loop                           │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 11  │ BacktestEngine.persistResult()                │ Strings         │ Manual JSON via concatenation                   │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 12  │ DelayDExecutor.compareDelays()                │ Wrapper Classes │ != on double with NaN risk                      │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 13  │ ReproducibilityService.computeDatasetHash()   │ Boilerplate     │ Manual hex instead of HexFormat                 │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 14  │ ReproducibilityService.serializeHyperparams() │ Strings         │ Manual JSON via StringBuilder                   │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 15  │ HistoricalDataReplay.extractDailyLastPrices() │ Strings         │ Substring on Instant for date                   │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 16  │ EquityStrategyRegistry                        │ DI              │ Manual new for 25 strategies                    │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 17  │ StrategyRegistry                              │ DI              │ Manual new for 30 strategies                    │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 18  │ BayesianWeightOptimizer.proposeUpdate()       │ JIT             │ Math.random() vs ThreadLocalRandom              │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 19  │ KpiProcessor.getRecentRates()                 │ Memory          │ Silent exception swallowing                     │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 20  │ ArrowIpcTransport.allocator                   │ Memory          │ RootAllocator never closed                      │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 21  │ MarketStressSimulator                         │ Wrapper         │ Unnecessary autoboxing from Map                 │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 22  │ RegimeDetector                                │ Strings         │ Invalid SLF4J format {:.2f}                     │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 23  │ RegimeDetector                                │ Loops           │ Operator precedence bug                         │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 24  │ DefaultPolygonWsClient.sendSubscribe()        │ Text Blocks     │ JSON via concatenation                          │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 25  │ FileOverflowBuffer                            │ Memory          │ ObjectMapper per instance                       │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 26  │ ToxicityMonitor.classifyToxicity()            │ Conditionals    │ if-return chain → ternary                       │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 27  │ FredClient fields                             │ Encapsulation   │ @Value on fields, not constructor               │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 28  │ NyFedClient fields                            │ Encapsulation   │ @Value on fields, not constructor               │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 29  │ EconomicCalendarClient fields                 │ Encapsulation   │ @Value on fields, not constructor               │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 30  │ OptionsDataClient fields                      │ Encapsulation   │ @Value on fields, not constructor               │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 31  │ TimescaleDbWriter.flushTicks()                │ Collections     │ Batch re-add on failure risks duplicates        │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 32  │ FileOverflowBuffer.hasData()                  │ JIT             │ toFile().length() vs Files.size()               │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 33  │ KernelAggregator.computeRealizedVariance()    │ Arrays          │ List<Double> autoboxing overhead                │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 34  │ FredClient.pollAllSeries()                    │ Loops           │ @Bulkhead at wrong granularity                  │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 35  │ TimescaleDbConfig                             │ Memory          │ Dead Flyway configuration                       │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 36  │ TimescaleDbConfig                             │ JIT             │ Dead code: configured Flyway unused             │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 37  │ ProxyDivergenceEvent                          │ Records         │ Missing compact constructor validation          │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 38  │ SignalQualityReportRepository.save()          │ OOP Design      │ 13-parameter method                             │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 39  │ TimescaleDbConfig                             │ DI              │ Redundant NamedParameterJdbcTemplate bean       │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 40  │ CdmOptionSnapshot                             │ Records         │ Missing Double.isFinite for Greeks              │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 41  │ CdmBondSnapshot                               │ Records         │ Missing Double.isFinite for rateDelta/rateGamma │ 🟡       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 42  │ KpiProcessor.computeVolatilityRegime()        │ Conditionals    │ if-else → switch expression                     │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 43  │ CorrelationEngine.getLatestValues()           │ Memory          │ Empty catch blocks                              │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 44  │ VirtualPortfolio.findOpenBySymbol()           │ Code Style      │ Returns null instead of Optional                │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 45  │ DelayDExecutor.executeWithDelay()             │ Loops           │ Two-pass variance → Welford                     │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 46  │ DefaultPolygonWsClient.session                │ Concurrency     │ Volatile check-then-act race                    │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 47  │ PolygonWsConfig                               │ Concurrency     │ Platform threads vs virtual threads             │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 48  │ DeRoundingFilter                              │ Loops           │ Minor: size() - 1 hoisting                      │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 49  │ FredClient.FRED_DATE_FMT                      │ Boilerplate     │ ISO_LOCAL_DATE constant exists                  │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 50  │ NyFedClient.NYFED_DATE_FMT                    │ Boilerplate     │ ISO_LOCAL_DATE constant exists                  │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 51  │ IdempotencyGuard.buildKey()                   │ Strings         │ + in non-loop context (fine)                    │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 52  │ DefaultPolygonWsClient.parseTick()            │ Arrays          │ Manual loop → IntStream                         │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 53  │ TimescaleDbWriter.batchSize                   │ Encapsulation   │ Package-private → private                       │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 54  │ TimescaleDbWriter.flushIntervalMs             │ Boilerplate     │ Unused field                                    │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 55  │ PolygonWsHandler                              │ OOP             │ Non-static inner class                          │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 56  │ AlphaSignalRecord                             │ Records         │ Inconsistent exception type                     │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 57  │ SignalQualityReportRepository                 │ Encapsulation   │ Returns Map<String,Object>                      │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 58  │ BacktestResultRepository                      │ Conditionals    │ LIKE on JSONB → ->>'name'                       │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 59  │ VirtualPortfolioPositionRepository            │ OOP             │ Unnecessary mapRow indirection                  │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 60  │ VirtualPortfolioTradeRepository               │ Encapsulation   │ rowMapper() creates new lambda each call        │ 🟢       │
  ├─────┼───────────────────────────────────────────────┼─────────────────┼─────────────────────────────────────────────────┼──────────┤
  │ 61  │ DemoController.getPortfolioSummary()          │ Collections     │ Mutable map → Map.ofEntries()                   │ 🟢       │
  └─────┴───────────────────────────────────────────────┴─────────────────┴─────────────────────────────────────────────────┴──────────┘

  Top recommendations by impact:
  1. Issues 5–7 (row mapper duplication) — single refactor eliminates ~80 lines of copy-paste across 3 repositories
  2. Issues 1–2, 8–9 (mutable arrays in records) — correctness bugs that break record immutability guarantees
  3. Issue 3 (thread-unsafe setters) — race condition in production singleton
  4. Issue 4 (== on Long) — silent correctness bug for IDs > 127
  5. Issues 35–36 (dead Flyway config) — baselineOnMigrate has zero effect right now
