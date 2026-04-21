# Scheduled Calibration Task Monitoring

## Overview

The `ScheduledCalibrationTask` recalibrates portfolio weights and indicator parameters on a fixed schedule. It uses historical data to optimize weights, then validates them on out-of-sample data before applying.

## Schedule

Default: every 30 days. Configured via:

```yaml
calibration:
  interval-ms: 2592000000   # 30 days in milliseconds
```

## Checking Calibration Results

Search for calibration output in backend logs:

```bash
grep "CalibrationResult" /var/log/tickonomics/backend.log | tail -5
```

A successful run logs:
```
CalibrationResult: improved=true oosSharpe=1.42 sharpeBefore=1.18 degradation=0.12
```

## Success Criteria

A calibration is considered successful when all of the following hold:

| Criterion          | Condition                      |
|--------------------|--------------------------------|
| `improved`         | `true`                         |
| `oosSharpe`        | > `sharpeBefore`               |
| `degradation`      | < 0.3                          |

If any criterion fails, the old weights are retained and a warning is logged.

## Manual Trigger

Option 1 -- POST to the demo portfolio endpoint (triggers recalibration as a side effect):

```bash
curl -X POST localhost:8080/api/v1/demo/portfolio
```

Option 2 -- restart the backend. The task runs once on startup if it has not run in the current interval.

## Post-Calibration Verification

Confirm the new weights were applied:

```bash
grep "WeightedWeightStore" /var/log/tickonomics/backend.log | grep "calibrated" | tail -3
```

Programmatic check:

```java
Map<String, Double> weights = weightedWeightStore.getCalibratedWeights();
// verify weights are non-null, non-empty, and sum to ~1.0
```

## Troubleshooting

### "Calibration failed" in Logs

1. Check historical data availability -- calibration requires 180 days of data:
   ```sql
   SELECT min(timestamp), max(timestamp), count(*)
   FROM tick
   WHERE timestamp > now() - INTERVAL '180 days';
   ```
2. If data is insufficient, wait for more data to accumulate or backfill from the data provider.

### "OOS validation failed" in Logs

The optimizer found weights that work in-sample but fail out-of-sample. This is normal during volatile market periods. The system keeps the previous weights. Monitor for repeated failures over consecutive runs.

### Optimizer Does Not Converge

Check the optimizer configuration in `application.yml`:

```yaml
calibration:
  max-iterations: 10000
  convergence-threshold: 0.0001
```

Increase `max-iterations` if convergence warnings appear frequently.

## Alerts

Monitor for these log entries and alert on-call:

```
Calibration failed
OOS validation failed
Calibration convergence not reached
```

## Related Runbooks

- [Distributed Tracing with Jaeger](distributed-tracing-jaeger.md) -- trace the calibration run end-to-end
