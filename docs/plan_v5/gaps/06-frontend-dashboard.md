# Frontend (Dashboard) — 2 Missing Items

**Plan ref:** `07-analytics-dashboard.md`

| #   | Item                     | Description                                                                                          |
| --- | ------------------------ | ---------------------------------------------------------------------------------------------------- |
| 1   | **D3.js integration**    | Plan specifies D3.js for heatmaps; current implementation uses only Perspective + lightweight-charts |
| 2   | **Playwright e2e tests** | Plan specifies Playwright; only Vitest unit tests exist                                              |

> **Note:** Perspective **is** used (CorrelationMatrix, SignalLog, LiquidityHeatmap). Auth **is** implemented (PKCE flow in `frontend/lib/auth.ts`). The 40 planned dashboard components are all implemented.
