🔴 Issue 1 — Mutable internal state exposed in record (TickData)  
  The conditions array in the TickData record is mutable and not cloned, allowing callers to modify internal state.

    1 // ✅ Fix
    2 public record TickData(
    3     Instant time, String symbol, BigDecimal price, long volume, int[] conditions) {
    4   public TickData {
    5     // ... other validations ...
    6     this.conditions = (conditions != null) ? conditions.clone() : null;
    7   }
    8   
    9   @Override
   10   public int[] conditions() {
   11     return (conditions != null) ? conditions.clone() : null;
   12   }
   13 }

  🔴 Issue 2 — Repeated switch logic (Strategy candidate) (KpiController)  
  The trend calculation logic based on status is duplicated in multiple methods, leading to maintenance overhead.

   1 // ✅ Fix
   2 private String determineTrend(String status) {
   3     return switch (status) {
   4         case "STRESSED" -> "ACCELERATING";
   5         case "ELEVATED" -> "DECELERATING";
   6         default -> "STABLE";
   7     };
   8 }
   9 // Usage: Determine trend once in a helper or within the DTO factory.

  🟡 Issue 3 — Broad Exception Catch (KpiController.parseWeights)  
  Catching Exception hides potential logic errors or unrecoverable system failures when only NumberFormatException is expected.

   1 // ✅ Fix
   2 } catch (NumberFormatException e) {
   3     log.warn("Failed to parse weight component", e);
   4     return new double[0];
   5 }

  🟡 Issue 4 — Hard-coded calculation logic in service (VirtualPortfolio.openPosition)  
  The complex logic for calculating position size and price targets is mixed with database persistence, violating single responsibility.

   1 // ✅ Fix - Extract to a dedicated PositionCalculator or similar component
   2 var calculation = positionCalculator.calculate(config, fillPrice, stopLossPct, takeProfitPct);
   3 VirtualPortfolioPosition position = new VirtualPortfolioPosition(
   4     null, Instant.now(), signal.symbol(), signal.direction(), calculation.quantity(), 
   5     fillPrice, null, null, calculation.stopLossPrice(), calculation.takeProfitPrice(), 
   6     null, null);

  🟢 Issue 5 — Use var for local types (KpiController, VirtualPortfolio, FredClient)  
  Multiple instances of redundant type repetition where the type is obvious from the right-hand side.

   1 // ✅ Fix
   2 var weights = parseWeights(latest.activeWeights());
   3 var positionSize = config.virtualBalance()...;
   4 var response = restClient.get()...;

  🟢 Issue 6 — Unnamed Boolean Conditions (FredClient.fetchSeries)  
  Complex filtering logic is used inline without a descriptive variable or predicate.

   1 // ✅ Fix
   2 Predicate<FredObservationRaw> isObservationValid = obs -> obs.value() != null && !".".equals(obs.value());
   3 return response.observations().stream()
   4     .filter(isObservationValid)
   5     .map(...)
   6     .toList();

  Summary Table

  ┌───┬─────────────────────────────────┬────────────────────┬─────────────────────────────────┬───────────┐
  │ # │ Location                        │ Category           │ Issue                           │ Severity  │
  ├───┼─────────────────────────────────┼────────────────────┼─────────────────────────────────┼───────────┤
  │ 1 │ TickData                        │ Encapsulation      │ Mutable array in record         │ 🔴 High   │
  │ 2 │ KpiController                   │ Design Patterns    │ Repeated switch logic           │ 🔴 High   │
  │ 3 │ KpiController.parseWeights()    │ Exception Handling │ Broad Exception catch           │ 🟡 Medium │
  │ 4 │ VirtualPortfolio.openPosition() │ OOP                │ Mixed persistence & calculation │ 🟡 Medium │
  │ 5 │ Multiple Files                  │ Modern Java        │ Redundant type repetition (var) │ 🟢 Low    │
  │ 6 │ FredClient.fetchSeries()        │ Conditionals       │ Unnamed boolean conditions      │ 🟢 Low    │
  └───┴─────────────────────────────────┴────────────────────┴─────────────────────────────────┴───────────┘

  I'll now provide a plan to fix these issues. I'll focus on the high-severity ones first.

   1. Fix TickData Record: Implement defensive cloning for the conditions array.
   2. Refactor KpiController: Extract the trend determination logic into a shared helper method or a DTO constructor.
   3. Refine Exception Handling: Specifically catch NumberFormatException in parseWeights.
   4. Refactor VirtualPortfolio: Extract calculation logic (this might require a new class or moving logic to DemoConfig).
   5. Apply var: Update the selected files to use var for local variables.
   6. Improve Readability: Use named predicates for complex stream filters in FredClient.




Java Best Practice Review — Tickonomics

Scope: Full project review across all 7 Java Gradle modules (~200+ source files, ~80 test files). Java 25 toolchain, Spring Boot 4.0.0.

---
Category 1 — Modern Java Features (SE 21–24)

🟡 Issue 1 — Platform-default charset in UUID derivation (BaseEquityStrategy.<init>, BaseOptionStrategy.<init>)
type.name().getBytes() uses the platform's default charset. While enum names are ASCII and all common charsets encode them identically, the intent should be explicit.

// ❌ Platform-dependent
this.id = UUID.nameUUIDFromBytes(type.name().getBytes());

// ✅ Explicit charset
this.id = UUID.nameUUIDFromBytes(type.name().getBytes(StandardCharsets.UTF_8));

🟢 Issue 2 — Flexible main method without public (TickonomicsApplication.main)
static void main(String[] args) (package-private) is valid in Java 21+ but is unconventional for a Spring Boot entry point. Spring Boot's own tooling, documentation, and community convention use public static void main. While functional, it may confuse developers and tools expecting the traditional signature.

// ✅ Conventional
public static void main(String[] args) {
    SpringApplication.run(TickonomicsApplication.class, args);
}

---
Category 2 — Reducing Boilerplate

Well done. The project makes excellent use of records: CdmInstrumentRef, CdmTick, StrategyContext, AlphaSignal, SecurityProperties, FinnhubProperties, ResilienceHealthSnapshot, FinnhubTrade, SanityBreach, VoteResult, DivergenceResult, DisasterAlert, TickEvent, LegGroup, and all persistence entities. Records are used consistently for data carriers. No issues found in this category.

---
Category 5 — String Immutability & the String Pool

🟡 Issue 3 — String concatenation for JSON in WebSocket messages (FinnhubWsClient.sendSubscribe, sendUnsubscribe)
// ❌ Error-prone JSON via string concatenation
private void sendSubscribe(String symbol) {
    sendMessage("{\"type\":\"subscribe\",\"symbol\":\"" + symbol + "\"}");
}
// ✅ Text block + type-safe formatting
private void sendSubscribe(String symbol) {
    sendMessage("""
            {"type":"subscribe","symbol":"%s"}""".formatted(symbol));
}

🟡 Issue 4 — String concatenation for JSON in backtest persistence (BacktestEngine.persistResult)
// ❌
"{\"strategy\":\"" + result.strategyName() + "\"}"
"[" + from + "," + to + "]"

// ✅ Text blocks
"""
{"strategy":"%s"}""".formatted(result.strategyName())
"[%s,%s]".formatted(from, to)

---
Category 7 — Conditionals Best Practices

🟡 Issue 5 — Deeply nested conditional logic with duplicated blocks (EventBasedTimeConverter.convert)
The convert method has identical 5-line blocks repeated four times in a deeply nested if-else chain. Each block only differs by the EventType enum value and the sign of expectingUp.

// ❌ 4 nearly-identical 5-line blocks
if (expectingUp && change >= theta) {
    events.add(new TickEvent(timestamps[i], price,
            EventType.DIRECTIONAL_CHANGE_UP,
            Math.abs(change), theta));
    lastExtremum = price;
    inOvershoot = true;
    overshootStart = price;
    expectingUp = true;
} else if (!expectingUp && change <= -theta) {
    // ... same 5 lines, different EventType
} // ... repeated twice more
Extract the duplicated logic into a helper that takes the EventType and the new expectingUp value as parameters.

---
Category 8 — Loops Best Practices

🟡 Issue 6 — Manual correlation/variance computation (ProxyDivergenceGuard.computeCorrelation, computeDivergenceScore)
Three-pass manual loops over the same lists duplicate index-based access. Use streams or a single pass.

// ❌ Three separate index-based loops
double sumX = 0, sumY = 0;
for (int i = 0; i < n; i++) { sumX += x.get(i).value(); sumY += y.get(i).value(); }
// ... second loop for covariance
// ... third loop for std deviation

// ✅ Single-pass or use DoubleStream
double[] xVals = x.stream().mapToDouble(RateSnapshot::value).toArray();
double[] yVals = y.stream().mapToDouble(RateSnapshot::value).toArray();
// ... compute correlation with one pass

---
Category 13 — Encapsulation

🔴 Issue 7 — Mutable array exposed via record accessor (TickData.conditions)
TickData is a record with an int[] conditions component. The auto-generated accessor returns the raw mutable array — any caller can corrupt the record's internal state. Compare with CdmTick which correctly clones the array in the constructor, overrides conditions(), and provides custom equals/hashCode.

// ❌ TickData — no array defense
public record TickData(
    Instant time, String symbol, BigDecimal price, long volume, int[] conditions) {
  public TickData {
    // validates non-null / range but never clones conditions
  }
  // auto-generated accessor returns the raw array — mutable!
}

// ✅ Follow CdmTick's pattern
public record TickData(
    Instant time, String symbol, BigDecimal price, long volume, int[] conditions) {
  public TickData {
    // ... validation ...
    conditions = conditions != null ? conditions.clone() : null;
  }
  @Override
  public int[] conditions() {
    return conditions != null ? conditions.clone() : null;
  }
  @Override
  public boolean equals(Object o) { /* use Arrays.equals for conditions */ }
  @Override
  public int hashCode() { /* include Arrays.hashCode(conditions) */ }
}

🔴 Issue 8 — Non-volatile mutable state in singleton bean (DisasterAlertClient)
consecutiveFailures (int) and circuitOpenUntil (Instant) are read/written from @Scheduled threads and potentially from HTTP request threads via hasCriticalAlerts(). No volatile, no synchronized, no AtomicInteger.

// ❌ Race condition
private int consecutiveFailures = 0;
private Instant circuitOpenUntil = Instant.MIN;

// ✅ Thread-safe
private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
private volatile Instant circuitOpenUntil = Instant.MIN;

🔴 Issue 9 — Non-thread-safe ArrayList in concurrent context (AlgorithmicSanityGuard.breaches)
breaches is a plain ArrayList written by recordBreach() (called from checkPriceMove/checkMessageRate) and read by getRecentBreaches()/isManualOversight(). No synchronization — risks ConcurrentModificationException and lost updates.

// ❌
private final List<SanityBreach> breaches = new ArrayList<>();

// ✅ Thread-safe, bounded, eviction-friendly
private final ConcurrentLinkedDeque<SanityBreach> breaches = new ConcurrentLinkedDeque<>();

🔴 Issue 10 — Non-volatile enabled flag in singleton (VotingClassifier)
setEnabled(boolean) writes to enabled without any happens-before edge to reads in vote(). Multiple threads see stale values indefinitely.

// ❌
private boolean enabled;
public void setEnabled(boolean enabled) { this.enabled = enabled; }

// ✅
private volatile boolean enabled;
// Or: private final AtomicBoolean enabled = new AtomicBoolean();

🟡 Issue 11 — Package-private mutable field (TimescaleDbWriter.batchSize)
// ❌ Mutable config value visible to the whole package
@Value("${writer.batch-size:500}")
int batchSize;

// ✅
@Value("${writer.batch-size:500}")
private int batchSize;

---
Category 16 — Dependency Injection

🟡 Issue 12 — Direct instantiation bypassing DI (EquityStrategyRegistry no-arg constructor)
// ❌ Hard-coded dependency — untestable, unswappable
public EquityStrategyRegistry() {
    this(new UniverseAggregator());
}

// ✅ Remove the no-arg constructor. Spring will inject UniverseAggregator
// via the parameterized constructor since it's a @Component.

🟡 Issue 13 — Field injection via @Value in 5 classes
FinnhubWsClient (wsUrl, reconnectBackoffMaxMs), FinnhubEquityClient (restUrl), TimescaleDbWriter (batchSize, flushIntervalMs), and SecurityConfig (allowedOrigins) use field-level @Value instead of constructor injection. This makes testing harder (cannot pass values directly) and hides dependencies.

// ❌ Field injection
@Value("${monitor.finnhub.ws-url:wss://ws.finnhub.io}")
private String wsUrl;

// ✅ Constructor injection
public FinnhubWsClient(
    ObjectMapper objectMapper,
    @Value("${monitor.finnhub.ws-url:wss://ws.finnhub.io}") String wsUrl) {
  this.wsUrl = wsUrl;
  // ...
}

🟡 Issue 14 — Null-returning API without Optional (FinnhubEquityClient.fetchQuote)
// ❌ Callers inevitably forget the null check → NPE
public FinnhubQuote fetchQuote(String symbol) { ... return null; }

// ✅
public Optional<FinnhubQuote> fetchQuote(String symbol) { ... return Optional.empty(); }

---
Category 19 — Memory Management & Leaks

🟡 Issue 15 — Unshut executor services (FinnhubWsClient)
asyncExecutor (virtual thread executor) and reconnectScheduler (scheduled executor) are created but never shut down. While virtual threads are daemon by default and the reconnect scheduler uses a daemon thread factory, the executor pools themselves are never released. Add @PreDestroy cleanup.

// ✅
@PreDestroy
public void destroy() {
    reconnectScheduler.shutdownNow();
    asyncExecutor.close(); // AutoCloseable in Java 19+
}

🟡 Issue 16 — Double-brace initialization anti-pattern (FinnhubEquityClient.doFetchHistoricalOhlcv)
// ❌ Creates anonymous inner class, leaks enclosing `this`, breaks serialization
return new java.util.ArrayList<>() {{
    for (int i = 0; i < timestamps.size(); i++) { ... add(...); }
}};

// ✅ Standard collection building
var result = new ArrayList<YahooOhlcv>();
for (int i = 0; i < timestamps.size(); i++) { ... result.add(...); }
return result;

---
Category 21 — Collections: Choosing the Right Type

🟡 Issue 17 — Exposing internal mutable queue (DisasterAlertClient.getAlertQueue)
// ❌ Caller can add/remove/clear the internal queue
Queue<DisasterAlert> getAlertQueue() {
    return alertQueue;
}

// ✅ Return an unmodifiable view or a snapshot
Queue<DisasterAlert> getAlertQueue() {
    return new ConcurrentLinkedQueue<>(alertQueue); // safe snapshot
}

---
What's Done Well

The codebase demonstrates strong practices in many areas:

- Records everywhere — ~60+ record types across all modules, replacing hand-rolled data classes. Excellent.
- Virtual threads — FinnhubWsClient correctly uses Executors.newVirtualThreadPerTaskExecutor() for I/O-bound trade dispatch.
- Sequenced collections — getFirst()/getLast() used in MomentumDecileStrategy, ProxyDivergenceGuard, and BacktestEngine.
- Constructor injection is the dominant pattern — aside from the 5 @Value field-injection cases noted above.
- Defensive health probes — CrossModuleResilienceHealthProbe wraps every external call in try-catch, never lets a probe failure crash the app.
- Strong test culture — Given-When-Then Spock specs + JUnit 5 tests, edge case coverage visible in MomentumDecileStrategySpec.
- Compact constructor validation in records is used consistently and correctly.
- CdmTick array defense is textbook-correct (clone in constructor, clone in accessor, custom equals/hashCode). Only TickData missed the same treatment.

---
Summary Table

┌─────┬───────────────────────────────────────────────────────────────┬───────────────┬─────────────────────────────────────────────────────────┬──────────┐
│  #  │                           Location                            │   Category    │                          Issue                          │ Severity │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 1   │ TickData record                                               │ Encapsulation │ Mutable array exposed via record accessor (no defensive │ 🔴 High  │
│     │                                                               │               │  copy)                                                  │          │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 2   │ DisasterAlertClient fields                                    │ Concurrency   │ Non-volatile consecutiveFailures/circuitOpenUntil       │ 🔴 High  │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 3   │ AlgorithmicSanityGuard.breaches                               │ Concurrency   │ Non-thread-safe ArrayList in multi-threaded context     │ 🔴 High  │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 4   │ VotingClassifier.enabled                                      │ Concurrency   │ Non-volatile flag with public setter in singleton       │ 🔴 High  │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 5   │ FinnhubEquityClient.doFetchHistoricalOhlcv                    │ Memory        │ Double-brace initialization — anonymous class + leak    │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 6   │ FinnhubWsClient                                               │ Resource mgmt │ Executor services never shut down (no @PreDestroy)      │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 7   │ BaseEquityStrategy.<init>                                     │ Modern Java   │ getBytes() without explicit charset                     │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 8   │ BaseOptionStrategy.<init>                                     │ Modern Java   │ getBytes() without explicit charset                     │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 9   │ FinnhubWsClient.sendSubscribe                                 │ Strings       │ String concatenation for JSON (use text blocks)         │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 10  │ BacktestEngine.persistResult                                  │ Strings       │ String concatenation for JSON (use text blocks)         │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 11  │ TimescaleDbWriter.batchSize                                   │ Encapsulation │ Package-private mutable field                           │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 12  │ EquityStrategyRegistry()                                      │ DI            │ Direct new UniverseAggregator() bypasses DI             │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 13  │ FinnhubEquityClient.fetchQuote                                │ API design    │ Returns null instead of Optional                        │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 14  │ DisasterAlertClient.getAlertQueue                             │ Encapsulation │ Returns internal mutable queue directly                 │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 15  │ EventBasedTimeConverter.convert                               │ Conditionals  │ 4× duplicated 5-line blocks in nested if-else           │ 🟡       │
│     │                                                               │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 16  │ FinnhubWsClient, FinnhubEquityClient, TimescaleDbWriter,      │ DI            │ @Value field injection instead of constructor injection │ 🟡       │
│     │ SecurityConfig                                                │               │                                                         │ Medium   │
├─────┼───────────────────────────────────────────────────────────────┼───────────────┼─────────────────────────────────────────────────────────┼──────────┤
│ 17  │ TickonomicsApplication.main                                   │ Modern Java   │ Unconventional static void main (package-private) for   │ 🟢 Low   │
│     │                                                               │               │ Spring Boot                                             │          │
└─────┴───────────────────────────────────────────────────────────────┴───────────────┴─────────────────────────────────────────────────────────┴──────────┘

4 High · 12 Medium · 1 Low — 17 findings across 18 files

The highest-priority fixes are the four 🔴 concurrency/encapsulation issues: TickData's missing array defense (data corruption), DisasterAlertClient's non-volatile state (race condition on circuit breaker), AlgorithmicSanityGuard's non-thread-safe breaches list (ConcurrentModificationException risk), and VotingClassifier's non-volatile enabled field (stale reads under concurrent access).