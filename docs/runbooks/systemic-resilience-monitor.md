# Systemic Resilience Monitoring Runbook

## Purpose

Monitor cross-module risk indicators and manage Global Safe Mode transitions to protect against adverse market conditions.

## Monitored Components

| Component | Location | Role |
|---|---|---|
| BehaviouralRiskProcessor (BRI) | Risk module | Detects anomalous trading patterns |
| RegimeDetector | Analytics module | Identifies market regime shifts |
| ProxyDivergenceGuard | Ingestion module | Detects data source dislocations |

## Dashboard

Monitor the **Global Safe Mode** indicator on the analytics dashboard at `http://localhost:3000/dashboard`. When lit, the system operates under reduced risk parameters.

## Check BRI Score

Query the behavioural risk index for elevated scores:

```sql
SELECT time, symbol, bri_score, risk_level
FROM behavioural_risk_index
WHERE bri_score > 0.7
ORDER BY time DESC
LIMIT 20;
```

BRI score thresholds:

| Score | Level | Action |
|---|---|---|
| < 0.3 | LOW | Normal operations |
| 0.3 - 0.7 | MEDIUM | Monitor closely |
| > 0.7 | HIGH | Contributes to Safe Mode trigger |

## Check Market Regime

Search backend logs for high-volatility regime detection:

```bash
docker compose logs backend --tail=500 | grep "RegimeType.HIGH_VOL"
```

Alternatively, query the regime state directly:

```sql
SELECT time, regime_type, confidence
FROM regime_history
ORDER BY time DESC
LIMIT 10;
```

## Safe Mode Triggers

Safe Mode activates when **all three** conditions are met simultaneously:

1. BRI score > 0.7 (HIGH)
2. Regime = `HIGH_VOL`
3. Proxy divergence detected (data dislocation)

## Safe Mode Behavior

When Global Safe Mode is active:

- Position sizes reduced by 50%
- Signal thresholds widened (higher confidence required)
- No auto-execute (manual confirmation required)
- Increased logging and monitoring frequency

## Recovery from Safe Mode

Safe Mode deactivates when **all three** conditions are sustained for 30 minutes:

1. BRI score < 0.3 (LOW)
2. Regime = `METASTABLE`
3. No proxy divergence detected

Verify recovery conditions:

```sql
SELECT MAX(bri_score) FROM behavioural_risk_index
WHERE time > NOW() - INTERVAL '30 minutes';
-- Must return < 0.3
```

```bash
docker compose logs backend --since=30m | grep "RegimeType.METASTABLE" | tail -1
```

## Cross-Module Health Check

Verify all system components are healthy:

```bash
# Backend health
curl -s http://localhost:8080/actuator/health | jq '.status'

# Analytics service health
curl -s http://localhost:8000/health | jq '.status'

# TimescaleDB connectivity
docker compose exec timescaledb pg_isready -U tickonomics
```

Expected: all three return healthy/ready status.

## Escalation

If Safe Mode triggers more than 3 times in a 24-hour window, escalate to the risk committee for manual review and potential parameter adjustment.
