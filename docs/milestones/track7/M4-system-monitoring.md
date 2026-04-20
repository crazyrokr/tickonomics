# Milestone 4: System Monitoring

**Status:** DONE
**Depends on:** M1 (Scaffolding, Layout, Auth, API Client)
**Estimated scope:** ~4 files

## Objective

Implement operational monitoring panels that show data source health, proxy divergence status, system infrastructure health, and real-time signal notifications.

## Components

### 4.1 Data Freshness Panel

**File:** `components/panels/DataFreshnessPanel.tsx`

Shows health and freshness of all data sources:

- **Direct FRED client** health and last sync timestamp (Finding 5)
- **Direct NY Fed client** health and last sync timestamp (Finding 5)
- **Proxy Divergence Guard** status (Finding 3):
  - Rolling 5-day T-Bill/SOFR correlation
  - Divergence score
  - `DISLOCATED` flag when > 2 std deviations from expected relationship
- OpenBB sidecar connectivity status (equity prices only)
- Last update timestamps per data source

### 4.2 System Health Panel

**File:** `components/panels/SystemHealthPanel.tsx`

- TimescaleDB health: connection status, compression status, continuous aggregate lag
- Circuit breaker status: OpenBB, Polygon WS, analytics worker (Resilience4j state)
- Chronicle Queue overflow depth (if applicable)
- JVM memory and thread pool metrics

### 4.3 Signal Toast Notifications

**File:** `components/signals/SignalToast.tsx`

- Real-time toast notifications on new signals via `/ws/signals`
- Signal type badge (ACTIONABLE, SPECULATIVE_STALE_MACRO, etc.)
- Auto-dismiss after 5s, click to navigate to chart
- Toast queue with max 5 visible, older ones stack behind

## Data Sources

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/health` | GET | Data source health, proxy divergence, circuit breakers |
| `/ws/signals` | WS | Real-time signal notifications |

## Acceptance Criteria

- [ ] Data freshness panel shows FRED client status with last sync time
- [ ] Data freshness panel shows NY Fed client status with last sync time
- [ ] Proxy divergence score and `DISLOCATED` flag displayed with correct coloring
- [ ] All data source timestamps update on refresh
- [ ] System health panel shows TimescaleDB compression and aggregate lag
- [ ] Circuit breaker states render correctly (CLOSED/OPEN/HALF_OPEN)
- [ ] Signal toast appears within 1s of WebSocket message
- [ ] Toast auto-dismisses and stacks correctly
- [ ] Unit tests for all three components with mock health data

## Technical Notes

- The health endpoint may aggregate multiple subsystem statuses; parse the response into typed interfaces
- Proxy divergence is a critical indicator — it should be visually prominent (not buried in a table)
- Signal toasts should use a portal to render above all other content
