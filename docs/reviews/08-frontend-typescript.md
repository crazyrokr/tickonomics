# Frontend & Landing Code Review

**Reviewed**: 2026-06-14
**Scope**: `/frontend/` (Next.js 16.2.6 dashboard) and `/landing/` (Next.js static export landing page)
**Files**: ~90 source files (57 dashboard, 22 landing, plus test files)
**Stack**: Next.js 16, React 19.2.4, TypeScript 5, TanStack Query 5, lightweight-charts 5.2, D3 7.9, FINOS Perspective 3.8, Tailwind CSS 4

---

## 1. Clean Code

### Component Decomposition -- GOOD

Components are well-decomposed by domain: `charts/`, `kpi/`, `panels/`, `layout/`, `dashboard/sections/`, `signals/`, `demo/`, `config/`. Each component has a single responsibility. The `DashboardClient` acts as a lightweight orchestrator, delegating to section components that compose individual KPIs and charts.

### Empty-State Pattern -- GOOD

The `lib/dashboard/empty-state.ts` file provides safe defaults for every not-yet-implemented component (54 exports). This is a clean pattern that avoids conditional rendering hell and allows the dashboard to render without crashes when backend endpoints are missing. Each default includes `isLoading: false` so components render their "no data" state rather than skeletons.

**Minor**: 21 of the 54 empty-state exports use `isLoading: false` with arrays of `[]` to trigger the component's empty message. This is consistent, but conflates "empty" with "not loading" -- a tri-state (`unavailable` | `loading` | `loaded`) would be more semantically precise.

### Custom Hooks -- GOOD

Seven custom hooks (`useKpiData`, `useSignals`, `useWebSocket`, `useHealth`, `useConfig`, `useDashboardData`, `useDemoData`) provide clean abstractions over TanStack Query. The `useDashboardData` hook composes all KPI hooks into a single typed return value, enabling sections to accept `DashboardData` as a single prop.

### Naming Conventions -- MINOR

- Sidebar `NAV_ITEMS` uses emojis as icon labels (`"📊"`, `"📈"`, `"🎯"`, `"🔍"`, `"⚙️"`). These render inconsistently across platforms. Consider SVG icons or a lightweight icon library.
- `KPI_REFETCH_INTERVAL_MS` / `KPI_HEAVY_REFETCH_INTERVAL_MS` -- clear and self-documenting.
- `emptyXxx` naming for defaults is clear and discoverable.

### Section Rendering in DashboardClient -- MINOR

```tsx
{activeSection === "overview" && <OverviewSection data={data} />}
{activeSection === "kpis" && <KpiSection data={data} />}
{activeSection === "charts" && <ChartsSection data={data} />}
{activeSection === "risk" && <RiskSection data={data} />}
{activeSection === "trading" && <TradingSection data={data} />}
{activeSection === "config" && <ConfigSection data={data} />}
```

A lookup map (`Record<SectionId, React.ComponentType<{data: DashboardData}>>`) would be more maintainable and avoid the chain of conditionals. However, all sections are mounted simultaneously (not conditionally), so each section's hooks fire regardless of which tab is active. This is discussed under Performance.

### Unused Parameters -- MINOR

`RiskSection` and `TradingSection` declare `_` as the parameter name for `data`, acknowledging that the DashboardData is intentionally unused. The comment in TradingSection explains the rationale. This is acceptable as a staging pattern but should be tracked for removal once backend endpoints ship.

---

## 2. TypeScript Best Practices

### Strict Typing -- GOOD

- `strict: true` in tsconfig.json (confirmed).
- No `any` types found in the codebase.
- `unknown` used appropriately for WebSocket messages and config values.
- Discriminated unions used for signal status codes (`SignalStatusCode`), ILI status (`IliStatus`), volatility regimes, connection states, and trend directions.

### Type Guard Gaps -- MEDIUM

**useWebSocket -- unsafe type assertion**

```ts
// hooks/useWebSocket.ts line ~33
const socket = (managerRef.current as unknown as { socket: WebSocket }).socket;
```

This bypasses TypeScript's access control to reach the private `socket` field of `WebSocketManager`. The `getState()` method and `onMessage` listener already provide the necessary API surface. The `send` function should be implemented as a method on `WebSocketManager` rather than reaching into internals.

**useSignals -- unvalidated cast**

```ts
// hooks/useSignals.ts line ~21
const update = lastMessage as SignalUpdate;
if (update?.signal) {
```

The cast from `unknown` to `SignalUpdate` has no runtime validation. A malicious or malformed WebSocket message would pass the `update?.signal` truthiness check with any object that has a `signal` property, potentially corrupting the signal list. A type guard or Zod schema validation should be used.

### API Type Mismatch -- HIGH

**Critical: snake_case vs camelCase discrepancy**

The auto-generated OpenAPI types (`lib/api/types.ts`) use snake_case property names:
- `formula_refs`, `expected_move`, `shape_xi`, `tail_var_999`, `response_path`

The manually written dashboard types (`types/api.ts`) use camelCase:
- `formulaRefs`, `expectedMove`, `shapeXi`, `tailVar999`, `responsePath`

The API client in `lib/api.ts` imports from `types/api.ts` (camelCase) and does `response.json() as Promise<T>` without any field name transformation. If the Java backend returns snake_case JSON (as the OpenAPI spec declares), **all camelCase property accesses will silently return `undefined` at runtime**, since TypeScript types are erased. This would break every data-driven component.

**Fix**: Either (a) add a transformation layer (e.g., `camelcase-keys`), (b) configure the Java backend (Jackson) to serialize camelCase, or (c) align the frontend types to match the actual wire format.

### Landing Type Inconsistency -- LOW

The landing's `lib/types.ts` defines `IliHistoryPoint` with `time` and `data_status` (snake_case), while the dashboard's `types/api.ts` defines the equivalent with `timestamp` and `status` (camelCase). These represent the same API response but use different field names, making the landing and dashboard type systems incompatible. A shared types package would prevent this drift.

### `readonly` Usage -- MINOR

The `SECTIONS` array in `lib/dashboard/sections.ts` uses `as const` assertion, making it readonly. This is good practice, but not consistently applied elsewhere (e.g., `NAV_ITEMS` in Sidebar is mutable).

---

## 3. Next.js Best Practices

### App Router -- GOOD

Both applications use the App Router consistently with `"use client"` directives only where needed. Server components are used for metadata, layout, and static content.

### Metadata -- GOOD

Both `layout.tsx` files define comprehensive metadata including OpenGraph and Twitter cards. The landing page includes `metadataBase`, `alternates.canonical`, and structured data (JSON-LD `SoftwareApplication` schema).

### Static Export (Landing) -- GOOD

The landing page uses `output: "export"` with `trailingSlash: true`, which is correct for a marketing site. `robots.ts` and `sitemap.ts` both declare `dynamic = "force-static"` as required for static export.

### No SSR/SSG/ISR Usage in Dashboard -- NOTE

The dashboard is entirely client-rendered -- all data fetching happens via TanStack Query on the client. This is appropriate for a real-time analytics dashboard behind authentication. No server-side data fetching is needed since the dashboard cannot be indexed and always requires fresh data.

**Observation**: There are no loading skeletons at the page level. The initial render before hydration is a blank white page. A minimal server-rendered shell with the header/sidebar would improve perceived performance.

### Image Optimization -- NOTE

The landing page sets `images: { unoptimized: true }` because `output: "export"` does not support Next.js image optimization. No `<Image>` components were found; static icons use emojis. This is acceptable for the MVP but should migrate to an optimized image pipeline for the OG image and any hero graphics.

### Route Handlers -- NOTE

No API routes, route handlers, or middleware are defined in the frontend. Authentication callback (`/api/auth/callback`) is expected to be handled by the backend or a BFF layer. The `buildAuthorizationUrl` function references `window.location.origin/api/auth/callback` -- ensure this route is actually handled.

---

## 4. React Best Practices

### Memoization -- GOOD

- `useMemo` in `useSignals` for merged signal list (depends on `query.data` and `lastMessage`).
- `useMemo` in `SignalToast` for `visibleSignals` slice.
- `useCallback` in `useWebSocket` for `send` function (stable reference).
- `useCallback` in `ConfigEditor` for `handleSubmit`.
- `useCallback` in `PriceIliChart` for `getOrCreateChart`.

### Missing Memoization Opportunities -- MINOR

- Dashboard section components (`OverviewSection`, `KpiSection`, etc.) could benefit from `React.memo` since they receive the same `data` reference when the active tab changes.
- The `Sparkline` component re-renders on every parent render despite being a pure SVG render. `React.memo` with a shallow comparison of the `data` array would be beneficial.

### Effect Cleanup -- GOOD

All `useEffect` hooks that create subscriptions (WebSocket, chart instances, ResizeObserver, Perspective workers) have proper cleanup functions using `disposed` flags, `clearTimeout`, and `.remove()` calls.

### SignalToast Auto-Dismiss Bug -- MEDIUM

```ts
useEffect(() => {
    if (visibleSignals.length === 0) return;
    const timer = setTimeout(() => {
      setDismissed((prev) => {
        const next = new Set(prev);
        for (const s of visibleSignals) {
          next.add(s.id);
        }
        return next;
      });
    }, autoDismissMs);
    return () => clearTimeout(timer);
  }, [visibleSignals, autoDismissMs]);
```

**Problem**: `visibleSignals` is derived from `useMemo` with `[signals, maxVisible]` dependencies. Every time a new signal arrives (which could be every 30-60 seconds via polling), `visibleSignals` changes, the effect cleanup fires, and the auto-dismiss timer resets. If signals arrive more frequently than `autoDismissMs`, the toasts **never dismiss**.

**Fix**: Track per-signal dismissal timers individually, or use a ref to store the first-seen timestamp and derive auto-dismiss from it rather than resetting on every change.

### Key Props -- GOOD

All list renderings use appropriate keys: `signal.id`, `section.id`, `row.symbol`, `item.href`, `${d.timestamp}-${i}` (for VolatilityClusterChart where entries might duplicate timestamps).

### Controlled Components -- GOOD

`ConfigEditor` manages `text` state internally with `useState`, and `SectionTabs` receives `active` as a controlled prop.

---

## 5. Financial UI Correctness

### Number Formatting -- GOOD

| Metric | Format | File |
|--------|--------|------|
| ILI value | `.toFixed(3)` | IliCard.tsx |
| Beta | `.toFixed(3)` | RepoEquityBetaTable.tsx |
| Anomaly score | `.toFixed(4)` | AnomalyScoreIndicator.tsx |
| Currency | `toLocaleString("en-US", { style: "currency", currency: "USD" })` | PortfolioSummary.tsx |
| Percentage (win rate) | `(value * 100).toFixed(1)%` | PortfolioSummary.tsx |
| Percentage (PnL) | `(value * 100).toFixed(0)%` | PortfolioTeaser.tsx |
| Strength/Confidence | `(value * 100).toFixed(0)%` | SignalShowcase.tsx |
| RRP drain | `.toFixed(1)B` | RrpDrainTrend.tsx |
| Liquidity stress | `.toFixed(3)` | LiquidityStressGauge.tsx |
| Systemic risk values | `.toFixed(4)` | LiquidityHeatmap.tsx |
| Correlation values | raw (via Perspective viewer) | CorrelationMatrix.tsx |

These are appropriate for their respective domains. ILI and beta with 3 decimal places match the precision of the underlying computation. Currency formatting uses `Intl.NumberFormat` via `toLocaleString`, which correctly handles locale-specific formatting.

### Color Convention -- GOOD

- Green = positive/healthy (ILI-green `#22c55e`): valid ILI, LOW_VOL, positive PnL, beta < 1, DECELERATING drain
- Red = negative/stressed (ILI-red `#ef4444`): DISLOCATED ILI, HIGH_VOL, negative PnL, beta > 1, ACCELERATING drain, kill-switch active
- Amber = caution (ILI-amber `#f59e0b`): DEGRADED ILI, suspect anomaly, PLI high, connecting
- Blue = informational (ILI-blue `#3b82f6`): NORMAL regime, primary action color

This follows standard financial dashboard conventions.

### Gauge Position Calculation -- NOTE

In `LiquidityStressGauge.tsx`:
```ts
const percentage = Math.min(Math.max((data.value + 1) / 2 * 100, 0), 100);
```

This maps the stress index (range: -1 to 1) to 0-100%. Without documentation of the index domain, this is correct for the stated semantic range but should be verified against the actual data distribution. The same pattern in VolatilityRegimeIndicator correctly uses `(currentPrice - lowerBand) / (upperBand - lowerBand)`.

### Timezone Handling -- MINOR

All time values use `new Date().toLocaleString()` or `new Date().toLocaleTimeString()`, which render in the user's local timezone. This is correct for a user-facing dashboard. No explicit timezone conversion or UTC offset display is present. For a financial application with global users, displaying the market timezone (e.g., "ET") alongside local time would be helpful but is not a defect.

### Chart Axis Formatting -- NOTE

- `lightweight-charts` axes use default formatting. Price axes on `PriceIliChart` inherit the library's auto-formatting. For financial charts, custom tick formatters with appropriate significant digits would improve readability.
- `Sparkline` SVG component has no axis labels -- this is by design for the compact sparkline format.

---

## 6. Security

### Authentication -- GOOD

The `auth.ts` module implements PKCE (Proof Key for Code Exchange) with SHA-256 code challenge:
- `crypto.getRandomValues()` for code verifier and state generation
- `crypto.subtle.digest("SHA-256")` for hashing
- `sessionStorage` for PKCE state persistence (cleared after exchange)
- `base64UrlEncode` correctly strips padding

### Token Storage -- MINOR

The auth token is stored in a cookie (`auth_token`) set by the backend and read by `getCookieValue()` in `lib/api.ts`. This cookie is not `HttpOnly` (it must be readable by JavaScript to attach to API requests). Consider:

1. Using `Secure` and `SameSite=Strict` cookie attributes (must be set server-side)
2. A BFF pattern where the Next.js server proxies API requests, keeping the token server-side only

### Environment Variables -- GOOD

All configurable values use `NEXT_PUBLIC_` prefix correctly:
- `NEXT_PUBLIC_API_BASE_URL`
- `NEXT_PUBLIC_AUTH_DISABLED`
- `NEXT_PUBLIC_AUTH_PROVIDER`
- `NEXT_PUBLIC_AUTH_BASE_URL`
- `NEXT_PUBLIC_AUTH_CLIENT_ID`
- `NEXT_PUBLIC_CONFIG_ADMIN`
- `NEXT_PUBLIC_WS_BASE_URL`

No secrets appear to be exposed. Sensitive values are kept server-side or in the backend.

### XSS Prevention -- GOOD

React's JSX auto-escapes all interpolated values. The only `dangerouslySetInnerHTML` usage is in `landing/app/page.tsx` for JSON-LD structured data, which is constructed from a static object literal (safe).

### CSRF Protection -- MINOR

No CSRF tokens are used for mutation endpoints (`PUT /api/v1/config`, `POST /api/v1/demo/kill-switch/activate`, etc.). The Bearer token in the Authorization header provides some protection against CSRF (since cookies are not automatically attached to cross-origin requests with `Authorization` headers), but a dedicated CSRF token for state-changing operations would be more robust.

### Config Admin Guard -- LOW

```ts
// ConfigSection.tsx
const isAdmin = process.env.NEXT_PUBLIC_CONFIG_ADMIN === "true";
```

This is a client-side-only guard. The `NEXT_PUBLIC_CONFIG_ADMIN` env var is embedded in the JavaScript bundle and visible to anyone who inspects the source. The actual authorization must be enforced server-side. The client-side check is acceptable as a UX optimization (hiding the editor), not as a security boundary.

---

## 7. Performance

### Code Splitting -- GOOD

- `lightweight-charts` is dynamically imported: `await import("lightweight-charts")` in both `PriceIliChart.tsx` and `IliChart.tsx` (landing).
- `@finos/perspective` and its plugins are dynamically imported in `SignalLog.tsx`, `CorrelationMatrix.tsx`, and `LiquidityHeatmap.tsx`.
- Landing uses `dynamic(() => import("./IliChart"), { ssr: false })` for the chart component.

These are the heaviest dependencies and are correctly loaded on demand.

### Bundle Size Considerations -- NOTE

- `@finos/perspective` (~2MB) is imported in three components but not deduplicated. Each component creates its own worker. A shared Perspective worker singleton would reduce memory usage.
- No bundle analysis tool (e.g., `@next/bundle-analyzer`) is configured.

### Re-render Optimization -- MEDIUM

**All dashboard sections fetch data regardless of visibility.** The `DashboardClient` conditionally renders only the active section, but all sections mount and fire their queries simultaneously because they are rendered via `&&` conditions that all evaluate (the inactive ones produce `false`, which React skips). However, this means the user pays the cost of all data fetching on initial load.

**Fix**: Use a lookup map pattern:
```tsx
const SECTION_COMPONENTS: Record<SectionId, React.ComponentType<{data: DashboardData}>> = { ... };
const ActiveSection = SECTION_COMPONENTS[activeSection];
return <ActiveSection data={data} />;
```

Note that even with this pattern, all hooks in `useDashboardData` would still fire because they are called unconditionally. To truly lazy-load: either (a) move hook calls into each section component, or (b) use `enabled` options on queries based on section visibility.

### Perspective Threshold -- GOOD

`LiquidityHeatmap` uses a `perspectiveThreshold` (default 10000) to decide whether to render with D3 (fewer data points) or Perspective (high-cardinality data). The `useState` initialization captures this decision once, avoiding mode switches during the component's lifetime.

### Stale Time and Refetch Configuration -- GOOD

| Data Type | staleTime | refetchInterval |
|-----------|-----------|-----------------|
| ILI, liquidity stress, beta, RRP, volatility | 10s | 30s |
| ILI history, correlation, systemic risk | 30s | 60s |
| Health | 5s | 15s |
| Signals | 30s | 60s |
| Config | 60s | none |
| Demo data | 10s | 30s |

These intervals are reasonable for near-real-time financial data without overloading the backend.

### Missing `React.memo` -- MINOR

The following pure presentational components would benefit from `React.memo`:
- `Sparkline` -- receives numeric arrays, pure SVG output
- `ConnectionStatus` -- receives a single string state
- `DisclaimerBanner` -- no props, always renders the same output
- `DemoBadge` / `LiveIndicator` -- simple badge components

---

## 8. Additional Findings

### Missing API Endpoint: Correlation Matrix -- HIGH

`useKpiData.ts` calls `getCorrelationMatrix()` which requests `/api/v1/kpi/correlation-matrix`. This endpoint does not appear in the auto-generated OpenAPI types (`lib/api/types.ts`). The OpenAPI spec lists `/api/v1/kpi/ili`, `/api/v1/kpi/ili/history`, etc., but not `correlation-matrix`. If this endpoint does not exist on the backend, the query will fail with a 404 on every refetch interval.

### Perspective Worker Lifecycle -- MINOR

`SignalLog.tsx`, `CorrelationMatrix.tsx`, and `PerspectiveHeatmap` (in `LiquidityHeatmap.tsx`) each create their own Perspective worker:
```ts
const worker = await perspective.worker();
```

Perspective workers are Web Workers that carry a non-trivial memory cost. Three workers on one page is excessive. A shared worker (via a module-level singleton or React context) would reduce memory pressure.

### Landing Mock Data as API Fallback -- NOTE

`landing/lib/api.ts` uses mock data as a transparent fallback when the API call fails:
```ts
catch {
    const fallback = mockFallbacks[path];
    if (fallback) {
      return fallback as T;
    }
    throw new Error(`No mock fallback for ${path}`);
}
```

This silently swallows network errors and substitutes stale mock data. For the landing page, this ensures the page always renders, which is good UX. However, the `SignalShowcase` component's error state text says "Showing demo data (API unavailable)" while `LiveDemo` shows no such indicator. **Make error visibility consistent across all landing components that use API data.**

### Landing `useApiData` Ignores Error -- LOW

`LiveDemo.tsx` destructures only `data` from `useApiData`, ignoring `error` and `isLoading`. When the API is unavailable, `iliData` will be the mock fallback array (from `fetchApi`'s catch clause), so the chart renders with mock data without indicating to the user that data is not live. Add an error indicator or at minimum pass `error` state to a visible element.

### `D3IliHeatmap` Unused with Real Data -- NOTE

`OverviewSection` renders `<D3IliHeatmap {...emptyD3IliHeatmap} />` alongside `SignalLog`, but no hook fetches data for it. The component is wired with the empty-state defaults and will always show "no data." Either the endpoint is pending (same as Tracks not yet implemented) or the component was included prematurely.

### Vitest/Playwright Configuration -- GOOD

Both projects have Vitest with `jsdom` environment for unit tests and Playwright for e2e tests. Test files follow the `__tests__/**/*.test.{ts,tsx}` pattern consistently.

---

## Summary of Findings by Severity

### HIGH (2 findings)

| # | Finding | File |
|---|---------|------|
| 1 | **API type field name mismatch**: OpenAPI spec declares snake_case; dashboard types use camelCase; no transformation layer exists. Risk: all property accesses return `undefined` at runtime. | `types/api.ts`, `lib/api/types.ts` |
| 2 | **Missing API endpoint**: `getCorrelationMatrix()` calls `/api/v1/kpi/correlation-matrix` which is absent from the OpenAPI spec. All correlation queries fail with 404. | `hooks/useKpiData.ts` |

### MEDIUM (3 findings)

| # | Finding | File |
|---|---------|------|
| 3 | **SignalToast auto-dismiss bug**: Timer resets on every signal change, preventing dismissal if signals arrive faster than `autoDismissMs`. | `components/signals/SignalToast.tsx` |
| 4 | **All sections fetch simultaneously**: Hooks fire for all sections regardless of active tab, wasting bandwidth and query capacity on hidden sections. | `hooks/useDashboardData.ts`, `components/dashboard/DashboardClient.tsx` |
| 5 | **WebSocket type safety**: Private `socket` field accessed via `as unknown as { socket: WebSocket }`, and `lastMessage` cast to `SignalUpdate` without runtime validation. | `hooks/useWebSocket.ts`, `hooks/useSignals.ts` |

### LOW / MINOR (8 findings)

| # | Finding | File |
|---|---------|------|
| 6 | Sidebar uses emoji icons; inconsistent rendering across platforms. | `components/layout/Sidebar.tsx` |
| 7 | `ConfigEditor` admin guard is client-side only; server must enforce authorization. | `components/dashboard/sections/ConfigSection.tsx` |
| 8 | Auth cookie lacks `Secure`/`SameSite` attributes (must be set by backend). | `lib/auth.ts` |
| 9 | No CSRF tokens for mutation endpoints. | `lib/api.ts` |
| 10 | Multiple Perspective workers created (one per component); memory-inefficient. | `SignalLog.tsx`, `CorrelationMatrix.tsx`, `LiquidityHeatmap.tsx` |
| 11 | Landing `LiveDemo` ignores API error state; silently shows mock data. | `landing/components/live-demo/LiveDemo.tsx` |
| 12 | Landing and dashboard have incompatible type definitions for the same API responses. | `landing/lib/types.ts`, `frontend/types/api.ts` |
| 13 | Landing mock data fallback silently catches all errors without distinguishing network failures from 4xx/5xx responses. | `landing/lib/api.ts` |

### NOTABLE POSITIVES

- **Test coverage**: 70+ unit test files covering components, hooks, and lib functions; Playwright e2e spec for the dashboard. All tests follow the project's Given-When-Then convention.
- **PKCE auth flow**: Well-implemented with proper cryptographic primitives.
- **Empty-state pattern**: `lib/dashboard/empty-state.ts` provides a systematic approach to handling unimplemented backend endpoints without UI crashes.
- **Dynamic imports**: Heavy libraries (Perspective, lightweight-charts) are correctly code-split.
- **TanStack Query configuration**: Appropriate stale times and refetch intervals for financial data freshness requirements.
- **Static export for landing**: Correct SEO configuration with robots.txt, sitemap.xml, OpenGraph, Twitter cards, and JSON-LD structured data.
- **Consistent financial formatting**: Correct use of `toFixed()`, `toLocaleString()`, and color conventions across all financial widgets.
- **TypeScript strict mode**: No `any` usage, proper discriminated unions, and well-structured interfaces.
