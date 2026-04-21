# Disaster Alert Verification

## Overview

The `DisasterAlertClient` polls external APIs for real-time disaster events (earthquakes, natural disasters). Significant events trigger an `EXOGENOUS_SHOCK` regime override in the risk model. The client uses a circuit breaker to handle upstream API failures gracefully.

## Polling Configuration

```yaml
disaster:
  poll-interval-ms: 30000   # 30 seconds
```

## Data Sources

| Source | Type          | URL                                              |
|--------|---------------|--------------------------------------------------|
| USGS   | Earthquakes   | `https://earthquake.usgs.gov/fdsnws/event/1/query` |
| GDACS  | General disasters | GDACS RSS feed                               |

## Check Alert Activity

```bash
grep "DisasterAlert" /var/log/tickonomics/backend.log | tail -20
```

Successful poll logs:
```
DisasterAlert: polled 3 events from USGS, max magnitude 5.2
```

## Verify USGS API Connectivity

```bash
curl -s "https://earthquake.usgs.gov/fdsnws/event/1/query?minmagnitude=5.0&format=geojson&limit=5" | jq '.metadata.count'
```

Expected: a numeric count > 0. A count of 0 or a connection error indicates an upstream issue.

## EXOGENOUS_SHOCK Trigger

An earthquake with magnitude >= 7.0 triggers the `EXOGENOUS_SHOCK` regime override:

```bash
grep "EXOGENOUS_SHOCK" /var/log/tickonomics/backend.log | tail -5
```

When triggered, the risk model overrides the current regime and moves to a defensive posture. Verify the override was applied by checking:

```bash
grep "RegimeOverride" /var/log/tickonomics/backend.log | tail -5
```

## Circuit Breaker

The client wraps external calls in a circuit breaker:

| Parameter          | Value |
|--------------------|-------|
| Failure threshold  | 5 consecutive failures |
| Open state duration | 60 seconds            |

When open, the client skips polling and returns cached/empty results.

Check circuit breaker state:

```bash
curl -s localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:disasterAlert | jq .
```

## Troubleshooting

### No Alerts Being Processed

1. Check if the circuit breaker is open:
   ```bash
   curl -s localhost:8080/actuator/metrics/resilience4j.circuitbreaker.failure.rate?tag=name:disasterAlert | jq '.measurements[0].value'
   ```
2. If failure rate is high, verify external API access from the server:
   ```bash
   curl -s -o /dev/null -w "%{http_code}" "https://earthquake.usgs.gov/fdsnws/event/1/query?minmagnitude=5.0&format=geojson&limit=1"
   ```
3. Check for DNS or network issues.

### Circuit Breaker Stuck Open

The circuit auto-closes after 60 seconds. If it stays open:
- Check for persistent network failures preventing recovery.
- Verify firewall rules allow outbound HTTPS to `earthquake.usgs.gov` and GDACS endpoints.

### Stale Shock Regime

If the `EXOGENOUS_SHOCK` regime persists after the event has passed, check the regime reset logic:

```bash
grep "RegimeReset" /var/log/tickonomics/backend.log | tail -5
```

The regime should auto-reset after the configured shock duration expires.

## Related Runbooks

- [Bulkhead Pool Monitoring](bulkhead-pool-monitoring.md) -- bulkhead limits around disaster polling
- [Distributed Tracing with Jaeger](distributed-tracing-jaeger.md) -- trace the alert processing pipeline
