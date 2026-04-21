# Distributed Tracing with Jaeger

## Overview

The backend emits OpenTelemetry traces to a Jaeger instance. Tracing covers the ingestion pipeline, ILI calculation, signal generation, and database writes. Use Jaeger to identify latency bottlenecks and correlate failures across services.

## Jaeger UI

```
http://localhost:16686
```

## Configuration

Tracing is configured in `application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1   # 10% of requests sampled
  otlp:
    tracing:
      endpoint: http://localhost:4317
```

Key properties:
- `management.tracing.sampling.probability` -- set to `1.0` to capture every trace (for debugging only).
- `management.otlp.tracing.endpoint` -- OTLP collector endpoint.

## Querying Traces

In the Jaeger UI:
1. Select service: `tickonomics`
2. Filter by operation: `IngestionPipeline.process`, `IliCalculator.compute`, `SignalGenerator.evaluate`
3. Set lookback period and click "Find Traces"

Via API:

```bash
curl -s "http://localhost:16686/api/traces?service=tickonomics&limit=20" | jq '.data[0].spans[] | {operationName, duration}' 
```

## Key Trace Spans

| Span Name                    | What It Covers                        |
|------------------------------|---------------------------------------|
| IngestionPipeline.process    | Tick validation, normalization, save  |
| IliCalculator.compute        | ILI score computation for an asset    |
| SignalGenerator.evaluate     | Signal generation from ILI scores     |
| CandleAggregator.aggregate   | OHLCV candle aggregation              |
| DisasterAlertClient.poll     | External disaster API polling         |

## Performance Analysis

1. Sort traces by duration (longest first) to find slow requests.
2. Expand a trace and look for spans with high wall-clock time.
3. Focus on DB write spans -- slow queries often indicate missing indexes or lock contention.
4. Compare span durations across time periods to detect regressions.

## Troubleshooting

### No Traces Appearing

1. Verify the OTLP collector is running:
   ```bash
   docker ps | grep otel-collector
   ```
2. Verify Jaeger is running:
   ```bash
   docker ps | grep jaeger
   ```
3. Check the endpoint config:
   ```bash
   curl -s localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("application")) | .property["management.otlp.tracing.endpoint"]'
   ```
4. Check backend logs for OTLP export errors:
   ```bash
   grep -i "otlp\|telemetry\|trace export" /var/log/tickonomics/backend.log | tail -10
   ```

### Sampling Too Low

For active debugging, set sampling to 100%:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

Reset to production value (`0.1` or lower) after debugging.

## Related Runbooks

- [Bulkhead Pool Monitoring](bulkhead-pool-monitoring.md) -- correlate bulkhead rejections with trace spans
- [Calibration Task Monitoring](calibration-task-monitoring.md) -- trace calibration runs
