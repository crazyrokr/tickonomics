# Consolidated Proposal: Data Quality Module

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)

## v3 Integration Points

| Track   | Component               | Role                                                           |
|---------|-------------------------|----------------------------------------------------------------|
| Track 2 | Database Schema         | `anomaly_score` column on `ili_history` and `rate_snapshots`   |
| Track 3 | Python Analytics Worker | PyTorch autoencoder service, drift-diffusion simulation        |
| Track 4 | Ingestion Layer         | Anomaly detection sidecar, SUSPECT_ANOMALY flagging            |
| Track 5 | Computation Engine      | ProxyDivergenceGuard enhancement, drift estimation             |
| Track 8 | Backtesting Framework   | Barrier hitting-time statistics, drift consistency diagnostics |

---

## Core Proposal: Autoencoder-Based Data Quality Management

**Source:** ML_DATA_QUALITY_MANAGEMENT (PRIMARY)
**Reference Paper:** `ssrn-3407885.pdf` ("Machine Learning and Quality Management of Quantitative Data" by Richard
Biegler-Konig and Daniel Oeltz, 2019)

### 1. Executive Summary

The paper proposes using **Autoencoders** for the quality management of quantitative financial data. Unlike traditional
threshold-based checks, autoencoders learn the "typical" shape and relationship of data objects (like forward curves)
and flag "corrupted" data based on reconstruction error (Mean Squared Error).

This approach provides a data-driven anomaly detection layer that adapts to the natural distribution of ILI components
rather than relying on fixed thresholds that may become stale as market conditions evolve.

### 2. Autoencoder Architecture

Train a simple autoencoder on historical ILI components:

- **Inputs:** RRP (Reverse Repo), SOFR/IORB spread, Volatility metrics.
- **Encoder:** Compresses the input vector into a latent representation.
- **Decoder:** Reconstructs the input from the latent representation.
- **Anomaly Score:** Reconstruction MSE (Mean Squared Error).

If the reconstruction MSE exceeds a learned threshold (e.g., 3x the training MSE), flag the data as `SUSPECT_ANOMALY`.

### 3. Track 4: Ingestion and Resilience Layer

#### Anomaly Detection Sidecar

Implement an `AnomalyDetectionWorker` in the Python analytics stack:

- Train the autoencoder on historical ILI component vectors.
- During ingestion, send the current data vector to the worker asynchronously.
- If the reconstruction MSE exceeds the threshold, flag the incoming data point as `SUSPECT_ANOMALY` before it enters
  the ILI computation pipeline.

### 4. Track 5: Computation Engine

#### Enhanced Proxy Divergence Guard

The paper demonstrates that autoencoders are sensitive to "erroneous shifts" and "shape corruptions." Use this property
to detect T-Bill/SOFR dislocation even when the 5-day correlation has not yet fully broken down, providing an earlier
warning for the `ProxyDivergenceGuard`.

### 5. Implementation Plan

1. **Analytics Worker (Track 3):** Create `app/services/anomaly_service.py` with a PyTorch-based Autoencoder class. The
   service exposes an async endpoint that the Java backend calls during ingestion.
2. **Java Backend (Track 4):** Update `DataQualityChecker` to include an asynchronous call to the
   `AnomalyDetectionWorker`. The call must not block the ingestion pipeline; if the worker is unavailable, proceed with
   traditional threshold checks.
3. **Persistence (Track 2):** Add `anomaly_score` column to `ili_history` and `rate_snapshots` hypertables. Store the
   MSE value and a boolean `is_suspect_anomaly` flag.

### 6. Validation Criteria

- [ ] Autoencoder correctly identifies the "corrupted" samples described in the paper (e.g., interchanged base/peak
  prices).
- [ ] False positive rate during "Normal" market regimes is < 1%.

---

## Complementary Additions

### A. Stochastic Drift and Hitting-Time Framework

**Source:** PROPOSAL_STOCHASTIC_DRIFT_INTEGRATION (COMPLEMENTARY)
**Reference:** Guo (2026), "Stochastic-Process Framework for Continuous-Time Financial Dynamics"

#### Overview

This proposal integrates a stochastic-process framework into the Computation Engine (Track 5) to improve drift and
volatility modeling beyond standard GARCH benchmarks. Together with the autoencoder anomaly detector, this forms a
two-layer data quality framework:

- **Layer 1 (Real-time):** Autoencoder detects sudden anomalies and corrupted data points during ingestion.
- **Layer 2 (Gradual):** Stochastic drift model monitors slow parameter drift that would not trigger anomaly thresholds
  but indicates a structural shift in the data generating process.

#### Drift-Diffusion Modeling

The paper formalizes a general one-dimensional Ito diffusion model where drift and volatility vary with time and state:

$$dX_t = \mu(t, X_t) dt + \sigma(t, X_t) dW_t$$

This provides a theoretically grounded framework for modeling market dynamics that go beyond the classical
Black-Scholes/GARCH models. Integrate this as an alternative to GARCH for volatility forecasting in the Analytics
Worker.

#### Barrier Hitting-Time Statistics

Incorporate "barrier hitting time" statistics as a new performance diagnostic in the backtesting framework (Track 8).
This evaluates strategy robustness during liquidity stress events by measuring how quickly a simulated path reaches
critical thresholds (e.g., ILI stress levels).

#### Consistency Diagnostics

The paper provides sufficient conditions for consistency in drift estimation. Apply these to:

- Refine proxy-adjusted funding rate projections.
- Improve accuracy of `IntradayProxyService` when official SOFR is unavailable.
- Detect when the current drift estimate has become inconsistent with incoming data, triggering a model recalibration.

#### Implementation Plan

1. **Analytics Worker (Track 3):** Add drift-diffusion simulation module with Ito process generation. Expose endpoints
   for barrier hitting-time calculations.
2. **Computation Engine (Track 5):** Replace simple GARCH volatility model with the Ito diffusion framework as the
   primary drift estimator.
3. **Backtesting (Track 8):** Integrate barrier hitting-time statistics into the robustness scan results.
4. **Proxy Service:** Apply drift estimation procedure to refine `IntradayProxyService` projections.

#### Integration Recommendation

Integrate the stochastic-process framework in Phase 2 as a robust replacement for simple GARCH models, providing a more
rigorous theoretical foundation for liquidity risk pricing. The autoencoder layer (core proposal) handles point-in-time
anomaly detection, while the drift-diffusion layer handles continuous parameter monitoring.

---

## Source Proposals

1. **ML_DATA_QUALITY_MANAGEMENT** (PRIMARY) - `ssrn-3407885.pdf` ("Machine Learning and Quality Management of
   Quantitative Data" by Biegler-Konig and Oeltz, 2019)
2. **PROPOSAL_STOCHASTIC_DRIFT_INTEGRATION** (COMPLEMENTARY) - Guo (2026), "Stochastic-Process Framework for
   Continuous-Time Financial Dynamics"
