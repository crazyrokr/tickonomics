# ADR-033: Money Values as BigDecimal / NUMERIC

## Status
Proposed

## Date
2026-06-17

## Context

The codebase historically represented all monetary values (prices, costs, PnL,
commissions, slippage, portfolio balances) as Java `double` backed by PostgreSQL
`DOUBLE PRECISION`. IEEE 754 binary floating-point cannot represent base-10
fractions exactly — `0.1 + 0.2 != 0.3` — and cumulative PnL across hundreds of
trades accumulates rounding error. This is a well-known defect in financial
systems.

This work is scoped to **critical-path only** (§7.3 of the elimination plan):
the three core money entities (VirtualPortfolioPosition, VirtualPortfolioTrade,
TickData), their repositories, the demo portfolio algebra, CDM Tick, and
WebSocket/controller surfaces. Computed statistics (Sharpe, win rate, z-scores,
correlations, factor returns, Greeks, volatility forecasts) remain `double` —
they are dimensionless ratios where IEEE 754 behaviour is acceptable and the
computational cost of BigDecimal arithmetic is prohibitive for vectorized/native
(TA-Lib) paths.

## Decision

Adopt `java.math.BigDecimal` for all money fields and `NUMERIC(precision, scale)`
in PostgreSQL/TimescaleDB. The migration is **additive**: new `NUMERIC` columns
are added alongside existing `DOUBLE PRECISION` columns (V36), backfilled, and
after validation the old columns are dropped and the new ones renamed to the
canonical names (V37).

### Precision strategy

| Category | SQL Type | Rationale |
|---|---|---|
| Prices, costs, PnL, monetary amounts | `NUMERIC(20,8)` | Supports values up to ~1e12 with 8 decimal places (sub-cent precision) |
| Share quantities | `NUMERIC(16,4)` | Supports up to ~1e12 shares with fractional shares to 4 decimal places |
| Rates, percentages, computed statistics | stays `DOUBLE PRECISION` | Dimensionless ratios; acceptable IEEE 754 behaviour; TA-Lib compatibility |

### Jackson serialization

`StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN` is enabled on the Spring Boot
auto-configured `JsonMapper.Builder` via a `JsonMapperBuilderCustomizer` bean
in `JacksonConfig`. This ensures BigDecimal values serialise as plain JSON
numbers (e.g., `"price": 520.50`) without scientific notation.

Jackson 3 (as bundled with Spring Boot 4.0) moved `WRITE_BIGDECIMAL_AS_PLAIN`
from `JsonWriteFeature` (JSON-specific) to `StreamWriteFeature` (format-agnostic).
The customiser interface is `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`
(the `Jackson2ObjectMapperBuilderCustomizer` name from Spring Boot 3 is not available).

### Fields staying `double`

All non-money fields retain their current types:
- Hit rates, Sharpe ratio, win rate, z-scores, correlations, p-values
- Greeks (delta, gamma, theta, vega, rho), implied volatility
- Factor returns, duration, convexity, dv01, yield values
- All bps rates (slippageBps, spreadCaptureBps, passiveBps, etc.)
- Percentages (positionSizePct, stopLossPct, takeProfitPct, etc.)
- Volume (share count)
- LeverageSignaler technical analysis fields (currentPrice, sma200, deviation)

### Files changed (critical path)

#### Persistence entities
- `VirtualPortfolioPosition` — `quantity`, `entryPrice`, `currentPrice`, `unrealizedPnl`, `stopLossPrice`, `takeProfitPrice` → `BigDecimal`
- `VirtualPortfolioTrade` — `quantity`, `fillPrice`, `commission`, `slippage`, `realizedPnl` → `BigDecimal`
- `TickData` — `price` → `BigDecimal`

#### CDM model
- `CdmTick` — `price` → `BigDecimal`; updated `equals`/`hashCode` for `BigDecimal.compareTo`

#### Repositories
- `VirtualPortfolioPositionRepository` — row mapper reads `_num` columns; INSERT writes both old and new columns; method signatures use `BigDecimal`
- `VirtualPortfolioTradeRepository` — same pattern
- `TickDataRepository` — same pattern

#### Web module
- `PriceTick` — `price` → `BigDecimal` (volume stays `double`)
- `DemoController` — `@RequestParam BigDecimal`, `Map<String, BigDecimal>` params
- New `JacksonConfig` — plain-number BigDecimal serialisation

#### Computation module
- `DemoConfig` — `virtualBalance` → `BigDecimal`
- `VirtualPortfolio` — all price/PnL/balance fields → `BigDecimal`; arithmetic uses `.add()`, `.subtract()`, `.multiply()`, `.divide()`
- `PortfolioManagementAlgebra` — `CostModel`, `MarginRequirement` fields → `BigDecimal`; K-M1 fix: `totalCost = t0Cost.add(t1Cost).add(spreadCost)`
- `KillSwitch` — `Map<String, Double>` → `Map<String, BigDecimal>`
- `PaperTradingEngine` — `ExecutionContext`, `TradeResult` money fields → `BigDecimal`
- `FillEstimate` — `fillPrice` → `BigDecimal`
- `PassiveExecutionHandler`, `SniperExecutionHandler` — construct `BigDecimal.valueOf(...)` for `FillEstimate`

#### Indicator/backtest files (read-only BigDecimal → double conversion)
- `PriceBasedIndicatorComputer`, `BacktestEngine`, `HistoricalDataReplay` — added `.doubleValue()` at `TickData.price()` call sites
- `CorrelationEngine`, `IntradayProxyService`, `ForecastPersistenceService` — same

#### Ingestion filters
- `TimePeriodicityFilter`, `DeRoundingFilter` — `TickData.price()` → `.doubleValue()`; new `TickData` construction uses `BigDecimal.valueOf(...)`

#### CDM adapters
- `AlphaVantageCdmAdapter`, `FinnhubEquityCdmAdapter`, `WsTradeCdmAdapter`, `YahooEquityCdmAdapter` — wrap raw `double` prices with `BigDecimal.valueOf(...)`

## Migration plan

1. **V36** (additive): `V36__add_numeric_money_columns.sql` — add `_num` NUMERIC columns,
   backfill from existing DOUBLE PRECISION columns. For `tick_data` (a hypertable),
   compression is toggled off/on following the V35 pattern.
2. **Code phase 1** (this PR): Entities use BigDecimal; repositories read/write `_num`
   columns (and write to old DOUBLE columns for rollback safety during transition).
3. **Validation**: Confirm no regressions in test suite, demo portfolio, and WebSocket
   broadcasts.
4. **V37** (cleanup, deferred): Drop old DOUBLE columns, rename `_num` columns to
   canonical names.
5. **Code phase 2** (deferred): Repositories switch back to canonical column names
   (no `_num` suffix).

## Consequences

- **Positive**: Exact decimal arithmetic eliminates PnL rounding errors; matches
  expectations of financial domain users. `0.1 + 0.2 == 0.3` now holds.
- **Negative**: BigDecimal arithmetic is slower than hardware `double` — acceptable for
  demo/virtual portfolio volumes (thousands of trades); unacceptable for TA-Lib
  indicator computation (which stays `double`).
- **Risk**: Migration touches hypertables (`tick_data`); V36 follows the V35 pattern
  of toggling compression off/on.
- **Scope limitation**: Full migration of all 35+ DOUBLE PRECISION columns across all
  tables is deferred. This critical-path-only approach covers the three core money
  entities and their immediate consumers.

## Alternatives considered

- **JSR 354 Money API (Moneta)**: Adds a dependency for functionality we do not
  need (currency conversion, formatting). BigDecimal is sufficient.
- **Stay on `double` everywhere**: Maintains status quo but perpetuates the
  rounding error issue identified in P-C1, W-C4, K-M1.
- **Adopt BigDecimal for ALL numeric fields including statistics**: Prohibitively
  expensive for TA-Lib/native indicator computation paths; dimensionless
  statistics do not benefit from exact decimal representation.

## Related

- Implements findings P-C1, W-C4, K-M1 from `docs/reviews/00-verification-report.md`
- Follows `docs/reviews/00-elimination-plan.md` §4, Workstream A
- V36 migration adds `_num` columns alongside V6/V1 columns (never rewritten)
- Follows ADR-032 (Spring Boot 4 / Jackson 3) for serialisation baseline
- Elimination plan §7.3 establishes the critical-path-only scope
