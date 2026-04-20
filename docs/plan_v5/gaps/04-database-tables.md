# Database — 2 Missing Tables

**Plan ref:** `02-database-schema.md`

| #   | Table                  | Status      | Note                                                                        |
| --- | ---------------------- | ----------- | --------------------------------------------------------------------------- |
| 1   | `order_flow_imbalance` | **MISSING** | V16 creates an index referencing it, but no `CREATE TABLE` exists           |
| 2   | `evt_risk_metrics`     | **MISSING** | V22 creates `risk_evt_parameters` instead; no hypertable `evt_risk_metrics` |
