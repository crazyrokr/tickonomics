# Resilience4j Bulkhead Pool Monitoring

## Overview

The backend uses Resilience4j bulkheads to limit concurrency and prevent resource exhaustion. Three pools are configured in `application.yml` under `resilience4j.bulkhead.instances`:

| Pool                   | Max Concurrent Calls | Purpose                          |
|------------------------|----------------------|----------------------------------|
| criticalIngestion      | 4                    | High-priority tick ingestion     |
| highVolumeIngestion    | 16                   | Bulk/batch ingestion             |
| computationEngine      | 8                    | Quantitative indicator computation |

## Monitor via Actuator

Check overall bulkhead metrics:

```bash
curl -s localhost:8080/actuator/metrics/resilience4j.bulkhead | jq .
```

Check a specific pool:

```bash
curl -s localhost:8080/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls?tag=name:criticalIngestion | jq .
```

```bash
curl -s localhost:8080/actuator/metrics/resilience4j.bulkhead.max.allowed.concurrent.calls?tag=name:computationEngine | jq .
```

## Warning Signs

Frequent `Bulkhead is full` errors in logs indicate saturation:

```bash
grep "Bulkhead is full" /var/log/tickonomics/backend.log | tail -20
```

If these appear regularly, the pool size needs tuning or upstream traffic needs throttling.

## Tuning Pool Sizes

Edit `application.yml`:

```yaml
resilience4j:
  bulkhead:
    instances:
      criticalIngestion:
        max-concurrent-calls: 8       # increased from 4
        max-wait-duration: 5s
      computationEngine:
        max-concurrent-calls: 12      # increased from 8
        max-wait-duration: 10s
```

Restart the backend for changes to take effect.

## Emergency: Temporary Pool Increase

Use the Spring Actuator environment endpoint to verify current config:

```bash
curl -s localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("application")) | .property.resilience4j'
```

Note: pool sizes cannot be changed at runtime through Actuator. You must restart the service after editing `application.yml`.

## Per-Pool Diagnostics

1. **available < 2 consistently** -- pool is near saturation. Consider increasing `max-concurrent-calls` or reducing upstream load.
2. **max-wait-duration exceeded** -- callers are timing out waiting for a permit. Increase `max-wait-duration` or reduce per-call processing time.
3. **Rejected calls metric climbing** -- the bulkhead is rejecting work. This protects the system but means data is being dropped.

```bash
curl -s localhost:8080/actuator/metrics/resilience4j.bulkhead.rejected.calls?tag=name:highVolumeIngestion | jq '.measurements[0].value'
```

## Related Runbooks

- [Distributed Tracing with Jaeger](distributed-tracing-jaeger.md) -- trace slow calls through bulkhead boundaries
