# ADR-003: External Validation Methodology

## Status

Accepted

## Context

The analytics microservice had 29 services with 55 public functions. Unit tests validated structural properties but never compared outputs against independent implementations. The core question — *"Is the platform's advice correct?"* — was unanswered.

## Decision

Implement a systematic cross-validation suite comparing every service against independent oracle implementations using **different algorithms** than the service uses. Organize tests into four groups:

1. **Group A (Deterministic Closed-Form):** Compare against analytically exact values using alternative math libraries (e.g., `math.erfc` vs `scipy.stats.norm`).
2. **Group B (Statistical Estimation):** Compare against `scipy.optimize.minimize` or `statsmodels` estimators.
3. **Group C (Stochastic Simulation):** Compare distributions against analytical solutions via KS tests.
4. **Group D (Heuristic/ML):** Validate mechanism correctness; mark untrainable models as UNVERIFIABLE.

Use `pytest.mark.xfail` for known bugs so they are tracked but do not break CI.

## Consequences

- 6 bugs discovered (3 CRITICAL, 1 HIGH, 2 MEDIUM)
- 22 services validated as CORRECT
- 3 services marked UNVERIFIABLE (inherently non-deterministic or untrained)
- All future service changes must pass both unit tests and external validation
- Bug fixes should remove the corresponding `xfail` marker and verify the test passes
