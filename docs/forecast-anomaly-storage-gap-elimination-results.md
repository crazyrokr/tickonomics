# Results: Forecast & Anomaly Storage Gap Elimination

## Status: COMPLETE

All three storage gaps identified in [`forecast-anomaly-storage-gap-elimination-plan.md`](forecast-anomaly-storage-gap-elimination-plan.md) have been implemented. Build and full test suite pass.

---

## Gap 3: Model State Persistence — DONE

Trained ML models are now persisted in `model_artifacts` table and reused via data-hash cache lookup.

| File | Type | Description |
|------|------|-------------|
| `persistence/.../db/migration/V30__add_model_artifacts.sql` | Migration | `model_artifacts` table with `state_data BYTEA`, `data_hash TEXT`, partial index on `(model_type, is_active)` |
| `persistence/.../entity/ModelArtifact.java` | Entity | Record: id, modelType, modelVersion, parameters, stateData, trainingStats, trainedAt, trainedRows, dataHash, gitSha, isActive |
| `persistence/.../repository/ModelArtifactRepository.java` | Repository | save, findActiveByModelType, findByModelTypeAndDataHash, deactivateOlderVersions, deactivateByTtl |
| `computation/.../model/ModelArtifactService.java` | Service | SHA-256 data hashing, cache lookup, model persistence with version pruning (keeps 3), TTL-based invalidation |
| `persistence/.../repository/ModelArtifactRepositoryTest.java` | Test | 6 tests: save, findActive (hit/miss), findByHash (hit/miss), deactivateOlderVersions, deactivateByTtl |
| `computation/.../model/ModelArtifactServiceTest.java` | Test | 9 tests: cache hit/miss, active lookup, persist parametric/NN, TTL invalidation, SHA-256 properties |

---

## Gap 2: Anomaly Results Wiring — DONE

Anomaly scores from the Python autoencoder are now written back to `ili_history` and `rate_snapshots` rows.

### Entity & Repository Changes

| File | Change |
|------|--------|
| `persistence/.../entity/IliHistory.java` | Added `anomalyScore` (Double) and `isSuspectAnomaly` (Boolean) fields |
| `persistence/.../entity/RateSnapshot.java` | Added `anomalyScore` (Double) and `isSuspectAnomaly` (Boolean) fields |
| `persistence/.../repository/IliHistoryRepository.java` | Added `updateAnomalyScore()`, `findLatestN()`; all SQL includes anomaly columns |
| `persistence/.../repository/RateSnapshotRepository.java` | Added `updateAnomalyScore()`, `findLatestN()`; all SQL includes anomaly columns; extracted `mapRow()` helper |

### Service

| File | Type | Description |
|------|------|-------------|
| `computation/.../anomaly/AnomalyScoringService.java` | Service | Reads latest N rows, builds feature vectors, calls Python `/train` or `/detect` (with model warm-start via `ModelArtifactService`), writes scores back. Guards: min 20 samples, min threshold, API failure tolerance |
| `computation/.../anomaly/AnomalyScoringServiceTest.java` | Test | 7 tests: sufficient rows → scores written, too few → skip, API failure → no exception, cached model → detect endpoint, threshold below minimum → skip, rate scoring with type filter |

### Downstream Fixes (record signature changes)

| File | Change |
|------|--------|
| `ingestion/.../fred/FredClient.java` | RateSnapshot constructor: 4 args → 6 args (null, null) |
| `ingestion/.../nyfed/NyFedClient.java` | RateSnapshot constructor: 4 args → 6 args (null, null) |
| `persistence/.../EntityValidationSpec.groovy` | All IliHistory (9→11) and RateSnapshot (4→6) constructors updated |
| `computation/.../kpi/KpiProcessorTest.java` | RateSnapshot constructor: 4 args → 6 args |
| `computation/.../kpi/IntradayProxyServiceTest.java` | RateSnapshot constructor: 4 args → 6 args |
| `computation/.../kpi/CorrelationEngineTest.java` | RateSnapshot constructor: 4 args → 6 args |
| `ingestion/.../quality/ProxyDivergenceGuardSpec.groovy` | 5 constructors fixed |
| `ingestion/.../quality/DataQualityCheckerSpec.groovy` | 4 constructors fixed |
| `ingestion/.../writer/TimescaleDbWriterSpec.groovy` | 1 constructor fixed |
| `ingestion/.../writer/IdempotencyRoutingSpec.groovy` | 2 constructors fixed |
| `ingestion/.../buffer/TieredIngestionBufferSpec.groovy` | 8 constructors fixed |

---

## Gap 1: Forecast Result Storage — DONE

GARCH volatility forecasts are now persisted per horizon step, with a scheduled back-fill for realized volatility.

| File | Type | Description |
|------|------|-------------|
| `persistence/.../db/migration/V29__add_volatility_forecasts.sql` | Migration | `volatility_forecasts` hypertable (7-day chunks), indexes on (symbol, time) and (model, time), 365-day retention policy |
| `persistence/.../entity/VolatilityForecast.java` | Entity | Record: time, symbol, model, horizonDays, forecastVol, realizedVol, maeVsBaseline, nObservations, parameters, gitSha, createdAt |
| `persistence/.../repository/VolatilityForecastRepository.java` | Repository | save, findBySymbolAndTimeBetween, findLatestBySymbol, updateRealizedVol |
| `computation/.../forecast/ForecastPersistenceService.java` | Service | Maps Python JSON response to forecast records (multi-step), persists each. `backfillRealizedVolatility()` computes annualized vol from tick_data. Guards: n_observations ≥ 50 |
| `persistence/.../repository/VolatilityForecastRepositoryTest.java` | Test | 4 tests: save, findBySymbolAndTimeBetween, findLatestBySymbol, updateRealizedVol |
| `computation/.../forecast/ForecastPersistenceServiceTest.java` | Test | 9 tests: valid response → 5 rows saved, insufficient observations → skip, error/null response → skip, empty forecast list → skip, correct vol per step, back-fill with ticks → realized vol updated, no elapsed forecasts → no updates, annualized vol computation (constant/rising/single tick) |

---

## Build Verification

```
$ ./gradlew compileJava compileTestJava test
BUILD SUCCESSFUL in 26s
38 actionable tasks: 9 executed, 29 up-to-date
```

Zero compilation errors, all tests pass.

---

## File Count

| Category | Count |
|----------|-------|
| New files | 14 |
| Modified files | 15 |
| Total | 29 |
| New tests | 7 test classes, 38 test methods |
