# ILI Weight Recalibration

## Overview

Index-Linked Instruments (ILI) portfolio weights are recalibrated periodically
by the `ScheduledCalibrationTask`. This runbook covers verification and manual
recalibration procedures.

## When Recalibration Occurs

- Automatically: every 30 days via `ScheduledCalibrationTask`.
- Manually: triggered through the stress/liquidity endpoint.

## Checking Current Weights

```bash
curl -s http://localhost:8080/api/v1/demo/portfolio | jq '.weights'
```

The response includes the current weight allocation and the last calibration
timestamp.

## Manual Recalibration

Trigger a weight re-evaluation:

```bash
curl -X POST http://localhost:8080/api/v1/stress/liquidity \
  -H "Content-Type: application/json" | jq .
```

This runs the calibration pipeline and returns updated weights.

## Reviewing Calibration Logs

```bash
docker compose logs backend --tail 500 | grep "CalibrationResult"
```

Key fields to review:

- `sharpeImprovement`: should be greater than 0.1 after calibration.
- `oosValidationPassed`: must be `true` for the new weights to be accepted.
- `calibrationDate`: confirms when the last run occurred.

## Verification Checklist

1. Confirm Sharpe improvement > 0.1 in the calibration result.
2. Verify out-of-sample (OOS) validation passed.
3. Compare weight drift from previous calibration:

   ```bash
   docker compose logs backend --tail 1000 | grep "WeightDelta"
   ```

4. Ensure no single weight exceeds the concentration limit.

## Rollback Procedure

If the new weights produce poor results, revert to the base weights stored in
`WeightedWeightStore`:

```bash
curl -X POST http://localhost:8080/api/v1/admin/weights/reset \
  -H "Content-Type: application/json"
```

This calls `resetToBase()` and restores the last known good weight set.

## Scheduling

The automated schedule is configured in the backend application:

```yaml
calibration:
  cron: "0 0 3 */30 * *"  # 03:00 every 30 days
  enabled: true
```

To temporarily disable automated calibration, set `calibration.enabled` to
`false` in the application configuration and restart the backend.
