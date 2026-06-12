# Plan: Eliminate Forecast & Anomaly Storage Gaps

## Context

Analysis of the Tickonomics codebase revealed three storage gaps in the forecasting and anomaly detection subsystems. All input data (ticks, rates, ILI history) is persisted in TimescaleDB, but **model outputs and trained model state are ephemeral** — lost when the API call completes. This plan closes each gap with specific schema migrations, entity/repository changes, and service wiring.

---

## Gap Overview

| # | Gap | Impact | Severity |
|---|-----|--------|----------|
| 1 | **Forecast results not stored** | No historical tracking of prediction accuracy; cannot compare forecasts to realized outcomes | High |
| 2 | **Anomaly columns unwired** | V9 added `anomaly_score` / `is_suspect_anomaly` to `ili_history` and `rate_snapshots` but no service writes to them | High |
| 3 | **Model state not persisted** | GARCH re-fits every request; autoencoder weights returned as base64 to caller memory only | Medium |

---

## Gap 1: Forecast Result Storage

### Problem

The Python analytics worker produces GARCH volatility forecasts and returns them as HTTP response bodies. Nothing writes these predictions to the database. There is no way to:
- Compare predicted vs. realized volatility after the fact
- Track forecast drift over time
- Back-test the forecasting pipeline itself

### Schema: `V29__add_volatility_forecasts.sql`

```sql
-- Store volatility forecast outputs for accuracy tracking

CREATE TABLE volatility_forecasts (
    time            TIMESTAMPTZ       NOT NULL,
    symbol          TEXT              NOT NULL,
    model           TEXT              NOT NULL DEFAULT 'garch',
    horizon_days    INTEGER           NOT NULL,
    forecast_vol    DOUBLE PRECISION  NOT NULL,        -- predicted annualized vol for this horizon step
    realized_vol    DOUBLE PRECISION,                  -- filled after horizon elapses
    mae_vs_baseline DOUBLE PRECISION,                  -- from the Python service response
    n_observations  INTEGER,
    parameters      JSONB,                             -- GARCH params: omega, alpha, beta, persistence
    git_sha         TEXT,
    created_at      TIMESTAMPTZ       NOT NULL DEFAULT now()
);

SELECT create_hypertable('volatility_forecasts', 'time', chunk_time_interval => INTERVAL '7 days');

CREATE INDEX idx_vf_symbol_time ON volatility_forecasts (symbol, time DESC);
CREATE INDEX idx_vf_model_time  ON volatility_forecasts (model, time DESC);

-- Retention: keep forecasts for 1 year (long enough for seasonal analysis)
SELECT add_retention_policy('volatility_forecasts', INTERVAL '365 days');
```

### Entity: `VolatilityForecast.java`

```java
package com.tickonomics.persistence.entity;

import java.time.Instant;

public record VolatilityForecast(
    Instant time,
    String symbol,
    String model,
    int horizonDays,
    double forecastVol,
    Double realizedVol,
    Double maeVsBaseline,
    Integer nObservations,
    String parameters,
    String gitSha,
    Instant createdAt) {}
```

### Repository: `VolatilityForecastRepository.java`

```java
// Key methods:
// save(VolatilityForecast) -> void
// findBySymbolAndTimeBetween(symbol, from, to) -> List<VolatilityForecast>
// findLatestBySymbol(symbol, limit) -> List<VolatilityForecast>
// updateRealizedVol(time, symbol, horizonDays, realizedVol) -> void
```

Follows the existing pattern: `@Repository` with `NamedParameterJdbcTemplate`, `MapSqlParameterSource`, inline `RowMapper` lambdas.

### Service Wiring

Add a `ForecastPersistenceService` in the `computation` module that:
1. Receives the Python analytics worker's JSON response
2. Maps each forecast step to a `VolatilityForecast` record
3. Persists via `VolatilityForecastRepository.save()`
4. Runs a scheduled job (`@Scheduled(cron = "0 0 0 * * *")`) that back-fills `realized_vol` for forecasts whose horizon has elapsed, by computing actual rolling volatility from `tick_data`

### Files to Create/Modify

| Action | File |
|--------|------|
| Create | `persistence/src/main/resources/db/migration/V29__add_volatility_forecasts.sql` |
| Create | `persistence/src/main/java/.../entity/VolatilityForecast.java` |
| Create | `persistence/src/main/java/.../repository/VolatilityForecastRepository.java` |
| Create | `computation/src/main/java/.../forecast/ForecastPersistenceService.java` |
| Create | `computation/src/test/java/.../forecast/ForecastPersistenceServiceTest.java` |
| Create | `persistence/src/test/java/.../repository/VolatilityForecastRepositoryTest.java` |

---

## Gap 2: Anomaly Results Wiring

### Problem

Migration V9 added `anomaly_score` (DOUBLE PRECISION) and `is_suspect_anomaly` (BOOLEAN) to both `ili_history` and `rate_snapshots`. However:
- The `IliHistory` and `RateSnapshot` Java records have **no fields** for these columns
- The `IliHistoryRepository` and `RateSnapshotRepository` have **no update methods** for them
- The anomaly detection API returns results as a transient HTTP response — nothing writes them back to the rows that were analyzed

### Step 2a: Update Entities

**`IliHistory.java`** — add two fields:

```java
public record IliHistory(
    Instant time,
    double iliValue,
    double zRrp,
    double zSpread,
    double zVol,
    String dataStatus,
    String activeWeights,
    String proxyDivergenceStatus,
    Double proxyDivergenceScore,
    Double anomalyScore,              // NEW
    Boolean isSuspectAnomaly) {       // NEW
  // ...
}
```

**`RateSnapshot.java`** — add two fields:

```java
public record RateSnapshot(
    Instant time,
    String rateType,
    double value,
    String source,
    Double anomalyScore,              // NEW
    Boolean isSuspectAnomaly) {       // NEW
  // ...
}
```

### Step 2b: Update Repositories

**`IliHistoryRepository.java`** — add method:

```java
void updateAnomalyScore(Instant time, double anomalyScore, boolean isSuspectAnomaly) {
    jdbc.update(
        "UPDATE ili_history SET anomaly_score = :score, is_suspect_anomaly = :anomaly "
            + "WHERE time = :time",
        Map.of("time", time, "score", anomalyScore, "anomaly", isSuspectAnomaly));
}
```

Update existing `save()`, `findByTimeBetween()`, and `findLatest()` SQL to include the two new columns.

**`RateSnapshotRepository.java`** — add method:

```java
void updateAnomalyScore(Instant time, String rateType, double anomalyScore, boolean isSuspectAnomaly) {
    jdbc.update(
        "UPDATE rate_snapshots SET anomaly_score = :score, is_suspect_anomaly = :anomaly "
            + "WHERE time = :time AND rate_type = :rateType",
        Map.of("time", time, "rateType", rateType, "score", anomalyScore, "anomaly", isSuspectAnomaly));
}
```

Update existing `save()`, `saveAll()`, query methods to include the two new columns.

### Step 2c: Service Wiring

Add an `AnomalyScoringService` in the `computation` module that:
1. Reads the latest N rows from `ili_history` and `rate_snapshots`
2. Converts to feature vectors (`list[list[float]]`)
3. Calls the Python `/api/v1/anomaly/train` endpoint (first run) or `/api/v1/anomaly/detect` (subsequent runs with cached model state)
4. Writes anomaly scores back to the corresponding rows via the new `updateAnomalyScore` methods
5. Scheduled on a configurable interval (default: every 15 minutes)

### Files to Create/Modify

| Action | File |
|--------|------|
| Modify | `persistence/src/main/java/.../entity/IliHistory.java` |
| Modify | `persistence/src/main/java/.../entity/RateSnapshot.java` |
| Modify | `persistence/src/main/java/.../repository/IliHistoryRepository.java` |
| Modify | `persistence/src/main/java/.../repository/RateSnapshotRepository.java` |
| Create | `computation/src/main/java/.../anomaly/AnomalyScoringService.java` |
| Create | `computation/src/test/java/.../anomaly/AnomalyScoringServiceTest.java` |
| Modify | `persistence/src/test/java/.../repository/IliHistoryRepositoryTest.java` |
| Modify | `persistence/src/test/java/.../repository/RateSnapshotRepositoryTest.java` |

---

## Gap 3: Model State Persistence

### Problem

Three families of ML models are trained per request with no reuse:

| Model | Training Cost | Current State |
|-------|--------------|---------------|
| GARCH(p,q) | ~0.5–2s per fit | Re-fitted from scratch every call |
| Autoencoder | ~5–30s (depends on epochs) | State returned as base64 string to caller; lost if caller restarts |
| CNN-LSTM regime | ~2–10s | Trained and discarded per request |

The autoencoder is the most expensive. Currently `train_autoencoder()` returns `model_state` as base64 and the caller must pass it back for `detect_anomalies()`. If the Java backend restarts, the model is lost and must be retrained from scratch.

### Schema: `V30__add_model_artifacts.sql`

```sql
-- Persist trained ML model state for warm-start

CREATE TABLE model_artifacts (
    id              BIGSERIAL         PRIMARY KEY,
    model_type      TEXT              NOT NULL,        -- 'garch', 'autoencoder', 'cnn_lstm_regime', 'rahf'
    model_version   TEXT              NOT NULL DEFAULT '1.0',
    parameters      JSONB             NOT NULL,        -- model-specific config (GARCH orders, encoding_dim, etc.)
    state_data      BYTEA,                             -- serialized model weights (PyTorch state_dict for NN, null for parametric)
    training_stats  JSONB,                             -- loss, epochs, threshold, etc.
    trained_at      TIMESTAMPTZ       NOT NULL DEFAULT now(),
    trained_rows    INTEGER,                           -- number of training samples
    data_hash       TEXT,                             -- SHA-256 of training data for cache invalidation
    git_sha         TEXT,
    is_active       BOOLEAN           NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_ma_type_active ON model_artifacts (model_type, is_active) WHERE is_active = TRUE;

-- Only keep the latest 3 active versions per model type
-- (enforced at application level during save)
```

### Entity: `ModelArtifact.java`

```java
package com.tickonomics.persistence.entity;

import java.time.Instant;

public record ModelArtifact(
    Long id,
    String modelType,
    String modelVersion,
    String parameters,
    byte[] stateData,
    String trainingStats,
    Instant trainedAt,
    Integer trainedRows,
    String dataHash,
    String gitSha,
    boolean isActive) {}
```

### Repository: `ModelArtifactRepository.java`

```java
// Key methods:
// save(ModelArtifact) -> long (generated id)
// findActiveByModelType(modelType) -> Optional<ModelArtifact>
// deactivateOlderVersions(modelType, keepLatestN) -> void
// findByModelTypeAndDataHash(modelType, dataHash) -> Optional<ModelArtifact>
```

### Service Wiring

**`ModelArtifactService`** in the `computation` module:

1. **Before training**: Compute a SHA-256 hash of the training data. Query `ModelArtifactRepository.findByModelTypeAndDataHash()`. If a matching artifact exists and `is_active = TRUE`, return it (skip training).
2. **After training**: Persist the new model via `ModelArtifactRepository.save()`. Call `deactivateOlderVersions(modelType, 3)` to keep only the latest 3 versions.
3. **For parametric models** (GARCH): Store fitted parameters (ω, α, β) in `parameters` JSONB, leave `state_data` NULL.
4. **For neural networks** (autoencoder, CNN-LSTM): Store PyTorch state_dict bytes in `state_data`, config in `parameters`.

The `AnomalyScoringService` (from Gap 2) and any future forecast orchestration would call `ModelArtifactService` to get cached models rather than retraining.

### Cache Invalidation

The `data_hash` column enables automatic cache invalidation:
- If the underlying data changes (new ticks/rates ingested), the hash changes
- A new model is trained and stored
- Old models are deactivated but kept for audit/comparison

A scheduled job can also invalidate models older than a configurable TTL (e.g., 24 hours for intraday, 7 days for daily models).

### Files to Create/Modify

| Action | File |
|--------|------|
| Create | `persistence/src/main/resources/db/migration/V30__add_model_artifacts.sql` |
| Create | `persistence/src/main/java/.../entity/ModelArtifact.java` |
| Create | `persistence/src/main/java/.../repository/ModelArtifactRepository.java` |
| Create | `computation/src/main/java/.../model/ModelArtifactService.java` |
| Create | `computation/src/test/java/.../model/ModelArtifactServiceTest.java` |
| Create | `persistence/src/test/java/.../repository/ModelArtifactRepositoryTest.java` |

---

## Dependency Order

```
Gap 1 (Forecast Results) ──────────────────────┐
                                                │
Gap 2 (Anomaly Wiring) ──── depends on ──────── Gap 3 (Model State)
                                                 │
                                                 └── do this first
```

**Recommended implementation order:**

1. **Gap 3** — Model state persistence (unblocks efficient anomaly scoring and forecast caching)
2. **Gap 2** — Anomaly wiring (depends on Gap 3 for autoencoder warm-start)
3. **Gap 1** — Forecast result storage (independent, can run in parallel with Gap 2 after Gap 3)

---

## Testing Strategy

All tests follow the Given-When-Then structure per project conventions.

### Unit Tests

| Test | Validates |
|------|-----------|
| `VolatilityForecastRepositoryTest` | Save, query by symbol/time, update realized vol, row mapper correctness |
| `ModelArtifactRepositoryTest` | Save, find active by type, find by data hash, deactivate old versions |
| `IliHistoryRepositoryTest` (updated) | New `updateAnomalyScore` method, updated row mappers include anomaly fields |
| `RateSnapshotRepositoryTest` (updated) | New `updateAnomalyScore` method, updated row mappers include anomaly fields |
| `ForecastPersistenceServiceTest` | Maps Python JSON response to entities, scheduled back-fill logic |
| `AnomalyScoringServiceTest` | Reads data, calls Python API, writes scores back, edge cases (empty data, API failure) |
| `ModelArtifactServiceTest` | Cache hit/miss, hash computation, version pruning, TTL expiry |

### Edge Cases

- **Empty training data**: `AnomalyScoringService` must skip if fewer rows than the Python service's minimum (20 samples)
- **API failure**: Model training/prediction failures must not block the scheduled job — log and continue
- **Concurrent writes**: `ModelArtifactService.deactivateOlderVersions()` must handle race conditions from multiple instances
- **Null realized_vol**: `VolatilityForecast` allows NULL `realizedVol` until the horizon elapses and actual data is available
- **Data hash collision**: SHA-256 hash with a size check as secondary validation
- **Model state too large**: Autoencoder state for high-dimensional data could exceed practical BYTEA size — cap `encoding_dim` at application level

### False-Positive Scenarios

- Anomaly scores should not be written when the autoencoder threshold is below noise level (verify `threshold > configurable_min`)
- Forecast persistence should reject forecasts with `n_observations` below the Python service's minimum (50 for GARCH)
- Model artifact cache should not return a model trained on a different `model_type` even if data hash matches

---

## Summary of All Files

### New Files (12)

| # | File | Module |
|---|------|--------|
| 1 | `persistence/.../db/migration/V29__add_volatility_forecasts.sql` | persistence |
| 2 | `persistence/.../entity/VolatilityForecast.java` | persistence |
| 3 | `persistence/.../repository/VolatilityForecastRepository.java` | persistence |
| 4 | `persistence/.../db/migration/V30__add_model_artifacts.sql` | persistence |
| 5 | `persistence/.../entity/ModelArtifact.java` | persistence |
| 6 | `persistence/.../repository/ModelArtifactRepository.java` | persistence |
| 7 | `computation/.../forecast/ForecastPersistenceService.java` | computation |
| 8 | `computation/.../anomaly/AnomalyScoringService.java` | computation |
| 9 | `computation/.../model/ModelArtifactService.java` | computation |
| 10 | `persistence/.../repository/VolatilityForecastRepositoryTest.java` | persistence (test) |
| 11 | `persistence/.../repository/ModelArtifactRepositoryTest.java` | persistence (test) |
| 12 | `computation/.../model/ModelArtifactServiceTest.java` | computation (test) |

### Modified Files (6)

| # | File | Change |
|---|------|--------|
| 1 | `persistence/.../entity/IliHistory.java` | Add `anomalyScore`, `isSuspectAnomaly` fields |
| 2 | `persistence/.../entity/RateSnapshot.java` | Add `anomalyScore`, `isSuspectAnomaly` fields |
| 3 | `persistence/.../repository/IliHistoryRepository.java` | Add `updateAnomalyScore`, update SQL to include anomaly columns |
| 4 | `persistence/.../repository/RateSnapshotRepository.java` | Add `updateAnomalyScore`, update SQL to include anomaly columns |
| 5 | `persistence/.../repository/IliHistoryRepositoryTest.java` | Update tests for new columns |
| 6 | `persistence/.../repository/RateSnapshotRepositoryTest.java` | Update tests for new columns |
