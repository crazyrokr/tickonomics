# Track 6: Landing Page (`tickonomics.io`)

**Phase:** Phase 3
**Can start:** Immediately (independent frontend project)
**Blocks:** Track 10 (Demo needs landing page integration)
**Depends on:** Nothing (can start in parallel with Track 1)

---

## Objective

Build a marketing landing page as a separate Next.js 15 application. This serves as the
public entry point to the platform, distinct from the analytics dashboard.

**Analysis findings applied:**

- **Finding 3 (Proxy Divergence Guard):** Live Demo section handles `DISLOCATED` ILI status
  from T-Bill/SOFR proxy divergence. Signals derived from dislocated proxy data are suppressed
  and not displayed.
- **Finding 6 (Dynamic Weighting):** ILI data may carry `DEGRADED_COMPONENT_STALE` status when
  a component has zero variance. The Live Demo section displays a degradation badge and indicates
  which components are excluded from the ILI calculation.
- **Finding 5 (Direct FRED/NY Fed Clients):** Data freshness indicators reference direct
  FRED/NY Fed/Yahoo Finance/Finnhub client status (all free sources) for core ILI data sources.

---

## Technology

| Layer     | Technology                                       |
|:----------|:-------------------------------------------------|
| Framework | Next.js 15 (App Router)                          |
| Styling   | TailwindCSS                                      |
| Language  | TypeScript                                       |
| Charts    | Lightweight Charts (for live demo embed)         |
| Analytics | Plausible or Umami (privacy-focused, no cookies) |
| Hosting   | Vercel or Cloudflare Pages (edge CDN)            |

---

## Page Sections

### 1. Hero Section

- Tagline and one-sentence value proposition.
- "Start Demo" CTA button linking to demo dashboard.
- Animated ILI chart preview (static SVG or lightweight Lottie animation).

### 2. Problem Section

Three pain points, each with icon and one-paragraph explanation:

1. **Liquidity blind spots** -- missing early warning signals.
2. **Lagging indicators** -- traditional metrics arrive too late.
3. **Manual correlation analysis** -- no automated funding-equity correlation.

### 3. How It Works Section

Three-step visual flow (horizontal stepper with icons):

1. **Ingest** -- data sources icons: Fed Reserve, FRED, Yahoo Finance, Finnhub.
2. **Analyze** -- ILI formula, correlation engine diagram.
3. **Act** -- signal badges: ACTIONABLE, SPECULATIVE.

### 4. Live Demo Section

- Embedded read-only chart showing real ILI data from the staging environment.
- Auto-refreshes daily (or shows cached snapshot if staging is down).
- Pulls data from staging API endpoint: `GET /api/v1/kpi/ili/history?limit=90`.
- **ILI status badges:** Display `VALID`, `DEGRADED_COMPONENT_STALE`, or `DISLOCATED` status
  alongside the chart (Findings 3, 6). Degraded status shows which components are excluded.
  Dislocated status hides proxy-derived data points and shows a "Awaiting official SOFR" notice.
- **Component weight display:** When status is `DEGRADED_COMPONENT_STALE`, show the `active_weights`
  JSONB (redistributed weights) in a small tooltip or footnote.
- **Regime Status Indicator (v4):** Badge showing current GARCH regime status pulled from
  `GET /api/v1/regime/garch`. Displays one of: `LOW_VOL`, `NORMAL`, `HIGH_VOL`, `EXOGENOUS_SHOCK`.
  Color-coded: green (LOW_VOL), blue (NORMAL), amber (HIGH_VOL), red (EXOGENOUS_SHOCK).
- **Anomaly Status (v4):** When the `is_suspect_anomaly` flag is active on the latest ILI history
  data point, a warning badge is displayed alongside the chart. Shows the `anomaly_score` (MSE
  value) in a tooltip. The badge uses an amber warning style to indicate data quality concern
  without blocking the display.
- **Participation Status (v4):** Indicator showing whether participation is currently `ADMISSIBLE`
  or `SUPPRESSED` with reason, pulled from `GET /api/v1/participation/status`. When suppressed,
  displays a small info badge with the suppression reason (e.g., "Regime: HIGH_VOL",
  "Anomaly detected", "Disaster alert active").

### 5. Signal Showcase

- Table of recent signals: symbol, direction, ILI percentile, timestamp, status badge.
- Status badges include: `ACTIONABLE`, `SPECULATIVE_STALE_MACRO`, `COST_EXCEEDS_EXPECTED_MOVE`,
  `COOLDOWN`, `INSUFFICIENT_DATA`. Dislocated signals are not displayed (suppressed by backend).
- Pulled from staging API: `GET /api/v1/signals?limit=10`.
- Read-only, no auth required.

### 6. Virtual Portfolio Teaser

- Live card showing demo portfolio performance from staging API.
- P&L, win rate, number of signals acted on.
- Pulled from: `GET /api/v1/demo/portfolio`.
- **Data integrity note:** Portfolio metrics exclude periods when ILI status was `DISLOCATED`
  or `DEGRADED_COMPONENT_STALE` to show only signal-verified performance (Findings 3, 6).

### 7. Pricing/Access Section

- "Open Source" badge.
- Link to GitHub repository.
- Self-hosted setup instructions.
- "Request Demo" contact form (optional).

### 8. Footer

- GitHub link.
- Documentation link.
- Disclaimer: "Not financial advice."

---

## SEO and Performance

- **Server-side rendering** with ISR (Incremental Static Regeneration):
    - Revalidate every 60s for live demo data routes.
- **OpenGraph meta tags** for social sharing.
- **Structured data:** JSON-LD for `SoftwareApplication`.
- **Lighthouse targets:**
    - Performance > 90
    - Accessibility > 95
    - SEO > 95
- **Responsive design:** mobile-first, works on all screen sizes.

---

## Analytics

- Page views, CTA clicks, demo signups tracked via **Plausible** or **Umami**.
- Privacy-focused: no cookies, GDPR-friendly.
- Self-hosted option available.

---

## Directory Structure

```
landing/
├── app/
│   ├── layout.tsx
│   ├── page.tsx                    # Main landing page
│   ├── api/
│   │   └── demo-data/
│   │       └── route.ts            # Proxy to staging API for ISR
│   └── globals.css
├── components/
│   ├── Hero.tsx
│   ├── ProblemSection.tsx
│   ├── HowItWorks.tsx
│   ├── LiveDemo.tsx
│   ├── SignalShowcase.tsx
│   ├── PortfolioTeaser.tsx
│   ├── PricingSection.tsx
│   └── Footer.tsx
├── lib/
│   └── api.ts                      # API client for staging data
├── public/
│   └── images/
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── Dockerfile
```

---

## Deployment

- Deployed separately from dashboard.
- `tickonomics.io` for landing, `app.tickonomics.io` for dashboard.
- Vercel or Cloudflare Pages with edge CDN.
- CDN cache invalidation for live demo data routes on deploy.

---

## API Dependencies (from staging/demo)

| Data                 | Endpoint                               | Used In                                               |
|:---------------------|:---------------------------------------|:------------------------------------------------------|
| ILI history          | `GET /api/v1/kpi/ili/history?limit=90` | Live Demo section                                     |
| Recent signals       | `GET /api/v1/signals?limit=10`         | Signal Showcase section                               |
| Demo portfolio       | `GET /api/v1/demo/portfolio`           | Portfolio Teaser section                              |
| System health        | `GET /api/v1/health`                   | Data freshness indicators (direct FRED/NY Fed status) |
| Regime status        | `GET /api/v1/regime/garch`             | Regime Status Indicator badge (v4)                    |
| Participation status | `GET /api/v1/participation/status`     | Participation Status indicator (v4)                   |

These are staging/demo API endpoints. If the API is unavailable, the page renders with
placeholder/cached data.

**API response fields used from findings:**

- `data_status`: `VALID`, `DEGRADED_COMPONENT_STALE`, `DISLOCATED` (Findings 3, 6)
- `proxy_divergence_status`: `DIVERGENT`, `SUPPRESSED`, or `null` (Finding 3)
- `active_weights`: redistributed component weights when degraded (Finding 6)

**v4 API response fields used:**

- `regime_status`: `LOW_VOL`, `NORMAL`, `HIGH_VOL`, `EXOGENOUS_SHOCK` (from `GET /api/v1/regime/garch`)
- `anomaly_score`: MSE value from autoencoder reconstruction (from ILI history)
- `is_suspect_anomaly`: boolean flag indicating suspected anomalous data (from ILI history)
- `participation_status`: `ADMISSIBLE` or `SUPPRESSED` with reason string (from `GET /api/v1/participation/status`)

---

## Validation

- [ ] All sections render correctly on desktop and mobile.
- [ ] Lighthouse scores meet targets (Performance > 90, Accessibility > 95, SEO > 95).
- [ ] ISR revalidation works for live demo data.
- [ ] Graceful fallback when staging API is unavailable.
- [ ] OpenGraph tags render correctly in social sharing previews.
- [ ] Responsive design works on all breakpoints.
- [ ] Analytics tracking fires on page view and CTA clicks.
- [ ] Build succeeds with `npm run build`.
- [ ] TypeScript compilation passes with zero errors.
- [ ] ESLint passes with zero errors.
- [ ] ILI status badges display correctly for `VALID`, `DEGRADED_COMPONENT_STALE`, `DISLOCATED` (Findings 3, 6).
- [ ] Dislocated data points are hidden with "Awaiting official SOFR" notice (Finding 3).
- [ ] Signal showcase displays all status badges correctly; no dislocated signals shown.
- [ ] Portfolio teaser excludes degraded/dislocated periods from performance metrics (Findings 3, 6).

**v4 additions:**

- [ ] Regime status badge displays correctly for all regime types (`LOW_VOL`, `NORMAL`, `HIGH_VOL`, `EXOGENOUS_SHOCK`).
- [ ] Anomaly warning badge shows when `SUSPECT_ANOMALY` flag is active on ILI history data.
- [ ] Participation status indicator reflects current admissibility (`ADMISSIBLE` / `SUPPRESSED` with reason).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|:--------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Proxy Divergence Guard, Dynamic Weighting, Direct FRED/NY Fed Clients. Added ILI status badges, component weight display, data integrity notes.                                                                                                                                                                                                                                                                                                        |
| v2      | No structural changes (external integration references only).                                                                                                                                                                                                                                                                                                                                                                                                                     |
| v3      | No structural changes (improvement proposal references only).                                                                                                                                                                                                                                                                                                                                                                                                                     |
| v4      | Added Regime Status Indicator badge in Live Demo section showing GARCH regime from `GET /api/v1/regime/garch`. Added Anomaly Status warning badge displaying `is_suspect_anomaly` flag and `anomaly_score` from ILI history. Added Participation Status indicator from `GET /api/v1/participation/status` showing ADMISSIBLE/SUPPRESSED with reason. Added three new API dependencies and four new API response fields. Added three validation criteria for v4 status indicators. |
| v6      | Updated data source references from OpenBB/Polygon to free sources (Yahoo Finance, Finnhub, FRED, Alpha Vantage). Updated data source icons in How It Works section. Updated Finding 5 text to reflect all free source clients. No structural changes to landing page sections. |
