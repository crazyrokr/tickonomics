# ADR-015: Analytics Dashboard Wiring (Track 7)

**Status:** Implemented
**Date:** 2026-06-13
**Decision:** Close the primary Track 7 (`07-analytics-dashboard.md`) gap — the dashboard page was a Milestone-1 scaffold; none of the 47 existing components were mounted on the live route. Wire every component onto `app/page.tsx` behind a section-tab layout, add the missing client `QueryClientProvider`, and back the wiring with unit and end-to-end tests.

## Context

The plan-v6 verification report scored Track 7 at ~80% and named its single most impactful gap: *"Dashboard page is a scaffold shell… None of the 47 components are mounted on the live route."* All 47 components (14 charts, 7 KPI cards, 19 panels, 2 config widgets, 2 signal widgets, `Sparkline`, 3 layout chrome) existed and were individually unit-tested, but `frontend/app/page.tsx` rendered only `Sidebar` + `Header` + placeholder text.

Exploration surfaced three blocking facts that shaped the design:

1. **No `QueryClientProvider` existed anywhere in the app.** The existing `useKpiData`/`useSignals` hooks call `useQuery`, which throws at runtime without a provider — so the data layer had never actually run in a browser.
2. **Only ~14 of the 47 components have a backing REST endpoint** today (the rest depend on Track 1 controllers the report flags as not yet built: regime, sentiment, climate, greeks, tournament, etc.). Those ~29 components are presentational and were verified to render safely with empty/zero data.
3. **The e2e suite was smoke-only** (6 loose checks) and depended on a backend that is not started in the Playwright `webServer`, so every real fetch failed.

## Decision

### 1. Client `QueryClientProvider` (the unblocker)

Added `frontend/app/providers.tsx` (`"use client"`) that creates one `QueryClient` via a `useState` initializer (singleton per browser tab / per test, never recreated on re-render; avoids the Next.js double-provider pitfall) and wraps children in `QueryClientProvider`. `frontend/app/layout.tsx` (server component) wraps `{children}` in `<Providers>`, establishing the standard App Router server→client boundary. Defaults: `staleTime: 10s`, `refetchOnWindowFocus: false`, `retry: 1`.

### 2. Data layer consolidation

- Extended `frontend/hooks/useKpiData.ts` with `useIliHistory`, `useCorrelationMatrix`, `useSystemicRiskHeatmap` (60s refetch / 30s stale, matching the existing KPI pattern).
- Added `frontend/hooks/useHealth.ts` (15s refetch — the most time-critical signal) and `frontend/hooks/useConfig.ts` (`useConfig`, `useConfigHistory`, and a `useUpdateConfig` mutation that invalidates both config queries on success).
- Added `frontend/hooks/useDashboardData.ts` — a single orchestrator composing all backed queries plus `useSignals` into one typed `DashboardData` object, so `DashboardClient` stays thin and all queries warm on mount.

### 3. Section-tab layout

`app/page.tsx` renders a `DashboardClient` (client) that owns `activeSection` state and renders a `SectionTabs` bar plus the active section only. Six sections group all 47 components (Overview, KPIs, Charts, Risk, Trading, Config). Only the active section's components mount, so inactive sections neither render nor fetch. `components/dashboard/SectionTabs.tsx` is a pure presentational `role="tablist"` driven by `lib/dashboard/sections.ts`.

### 4. Mount all 47 components; empty-state policy

Per the report's #1 priority, every component is mounted. Backed components receive real data from `useDashboardData`; the ~29 unbacked components render from typed safe defaults in `lib/dashboard/empty-state.ts`. The empty-state policy is deliberately **non-fabricating**: array props receive `[]` so the component shows its own "no data" message, and object-gated optional props receive `undefined` so the component renders its own loading skeleton and auto-resolves once its Track 1 endpoint ships. No zero values are invented.

### 5. Sidebar left independent

`components/layout/Sidebar.tsx` is unchanged; its existing unit test passes unmodified. In-page section navigation is owned by `SectionTabs`, avoiding a rewrite of the sidebar's `<a href>` model. (The sidebar's `/charts`, `/kpis`, etc. links remain a pre-existing dead-link follow-up, out of scope.)

### 6. D3 dependency pin (incidental fix)

`next build` runs `tsc` and failed on `D3IliHeatmap`'s `.join()` calls — `@types/d3-selection@1.0.10` (pulled in transitively by `d3-svg-legend` via `@finos/perspective-viewer-d3fc`) lacks `.join()`. Pinned `@types/d3-selection@^3` as a direct devDependency; the correct `3.0.11` is now hoisted, clearing all 12 pre-existing d3-selection type errors without touching the component. The hand-written d3 mock in `D3IliHeatmap.test.tsx` was also replaced with a chainable `Proxy` mock so the data-join tests no longer break on `.attr().attr()` chaining.

### 7. End-to-end suite

Rewrote `e2e/dashboard.spec.ts` to mock the backend via `page.route(/localhost:8080/, …)` (function-fulfilled JSON fixtures with CORS headers, including OPTIONS preflight handling) and to wait for a client-rendered signal (`ili-status-badge`) before interacting — guaranteeing hydration. Five tests cover chrome render, each section tab switch, mocked-data rendering, and a zero-app-error gate. WebSocket connection failures (no backend in the e2e environment; phrased differently by Chromium vs Firefox) are filtered from the error gate.

## Consequences

- The live `/` route now renders the full dashboard: real data where endpoints exist, honest empty/skeleton states where they do not. Landing the Track 1 controllers will fill those in with no frontend change beyond hook wiring.
- All green: `tsc --noEmit` clean, **371 unit tests pass** (59 files), `next build` succeeds, **10 Playwright tests pass** (Chromium + Firefox).
- New unit coverage: providers, `useDashboardData`/`useConfig` hooks (Given-When-Then), a 29-component empty-state safety net, `DashboardClient` tab switching, `SectionTabs`.

## Known limitations / deferred

- **D3 migration deferred** (per scope decision): the QQ/ACF/convergence/heatmap family still uses hand-rolled SVG; only `D3IliHeatmap` uses the `d3` library. Functionally fine; deferred as a separate task.
- **`PriceIliChart` timestamp assumption**: the chart passes `IliHistoryPoint.timestamp` straight to `lightweight-charts` as `Time`, which expects `yyyy-mm-dd` (business day) or a UNIX timestamp. ISO-with-time strings throw `"Invalid date string"`. The unit test mocks the chart and does not catch this; the e2e fixture uses daily `yyyy-mm-dd` timestamps. A production backend emitting intraday ISO timestamps would need `PriceIliChart` to convert — a follow-up.
- **Perspective WASM in dev**: `CorrelationMatrix`/`SignalLog`/`LiquidityHeatmap` load `@finos/perspective`; in the dev server the WASM asset is not always served (`Missing perspective-client.wasm`). These components are covered by unit tests; the e2e asserts non-Perspective charts instead. Production asset serving is a deployment follow-up.
- **Unbacked components** show empty/skeleton states until their Track 1 endpoints are implemented.
