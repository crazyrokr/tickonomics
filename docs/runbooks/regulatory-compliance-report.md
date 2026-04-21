# Regulatory Compliance Report Generation Runbook

## Purpose

Generate and manage self-certification reports meeting MiFID II requirements for algorithmic trading oversight.

## Report Storage

Reports are stored in the `regulatory_compliance_reports` table, created by the V14 database migration. Each report contains:

| Field | Description |
|---|---|
| `total_signals` | Count of all signals generated in reporting period |
| `stressed_market_intervals` | Intervals where Safe Mode was active |
| `kill_switch_tests` | Number of Big Red Button tests performed |
| `otr_breaches` | Order-to-Trade Ratio violations detected |
| `compliance_status` | `COMPLIANT` or `NON_COMPLIANT` |

## Generate a Report

Trigger report generation via the API:

```bash
curl -X POST http://localhost:8080/api/v1/compliance/report \
  -H "Content-Type: application/json" \
  -d '{
    "period_start": "2026-05-01T00:00:00Z",
    "period_end": "2026-05-31T23:59:59Z"
  }'
```

Expected response:

```json
{
  "report_id": "RPT-2026-05-001",
  "status": "GENERATED",
  "compliance_status": "COMPLIANT"
}
```

## Review Report Contents

```sql
SELECT report_id, period_start, period_end, total_signals,
       stressed_market_intervals, kill_switch_tests, otr_breaches,
       compliance_status, generated_at
FROM regulatory_compliance_reports
ORDER BY generated_at DESC
LIMIT 5;
```

## Schedule

| Frequency | Action |
|---|---|
| Monthly | Generate compliance report for previous month |
| Quarterly | Review all monthly reports, compile quarterly summary |
| Ad-hoc | Generate on-demand after any Safe Mode incident |

## Kill Switch Test

Verify the Big Red Button functions correctly at least monthly:

```bash
# 1. Execute test halt
time curl -X POST http://localhost:8080/api/v1/demo/stop

# 2. Verify response time < 50ms
# 3. Confirm halt via health check
curl -s http://localhost:8080/actuator/health | jq '.components.paperTradingEngine.status'

# 4. Restore operations
curl -X POST http://localhost:8080/api/v1/demo/start

# 5. Record test in compliance report
```

The `kill_switch_tests` field in the report should reflect all test executions.

## OTR Breach Check

Order-to-Trade Ratio breaches are derived from the toxicity scores:

```sql
SELECT time, symbol, otr_ratio, toxicity_level
FROM toxicity_scores
WHERE otr_ratio > 50.0
ORDER BY time DESC
LIMIT 20;
```

A breach is recorded when `otr_ratio` exceeds the configurable threshold (default: 50.0).

## Export for Audit Trail

Export report to PDF or CSV for external audit:

```bash
# CSV export
curl -s http://localhost:8080/api/v1/compliance/report/RPT-2026-05-001/export?format=csv \
  -o compliance_report_2026_05.csv

# PDF export
curl -s http://localhost:8080/api/v1/compliance/report/RPT-2026-05-001/export?format=pdf \
  -o compliance_report_2026_05.pdf
```

## Non-Compliance Response

If `compliance_status` is `NON_COMPLIANT`:

1. Identify the failing metric from the report
2. Document root cause analysis
3. Implement corrective action
4. Re-generate report after remediation
5. Retain both compliant and non-compliant reports for audit
