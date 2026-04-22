# Track 6: Landing Page Implementation Plan

## Context

Tracks 1–5 of the Tickonomics implementation plan are DONE. The next track in phase order is **Track 6: Landing Page (Phase 3)** — a Next.js 15 marketing page at `tickonomics.io`. This is a greenfield frontend module: the repository has zero Node.js tooling, no Docker, and no CI/CD. The backend (Spring Boot on port 8080) has CORS open on `/api/**` and 7 API endpoints (mostly returning stubs). The detailed product spec exists at `docs/plan_v5/06-landing-page.md`.

## Objective

Build a production-ready Next.js 15 static marketing page with: Hero, Problem, How It Works, Live Demo (with ILI chart + status badges), Signal Showcase, Portfolio Teaser, Pricing, and Footer sections. All dynamic sections gracefully degrade to mock data since most API endpoints don't exist yet.

## Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Module location | `landing/` standalone directory | No Java dependency on it; not added to `settings.gradle` |
| Output mode | Static export (`output: 'export'`) | CDN-deployable, highest Lighthouse scores, no Node runtime needed |
| Styling | Tailwind CSS 4 | Smallest CSS bundle, zero runtime |
| Charting | lightweight-charts 4 | 45KB gzipped, purpose-built for financial data |
| Animations | CSS `@keyframes` + `IntersectionObserver` | Zero JS cost, critical for Lighthouse > 90 |
| Data fetching | SWR (client-side) | Compatible with static export; stale-while-revalidate UX |
| Testing | Vitest + React Testing Library | Native Vite integration, Given-When-Then structure |
| No framer-motion | Deliberate | Saves 30KB, CSS animations suffice for scroll-reveal |

## API Gap Reality

Only 2 of 6 required endpoints exist (both return empty data). Every dynamic component MUST render with static mock fallback:

| Endpoint | Exists? | Landing Section |
|---|---|---|
| `GET /api/v1/quant/signals/active` | YES (empty) | Signal Showcase |
| `GET /api/v1/quant/strategies/active` | YES (empty) | Live Demo |
| `GET /api/v1/kpi/ili/history?limit=90` | NO | Live Demo ILI chart |
| `GET /api/v1/regime/garch` | NO | Regime badge |
| `GET /api/v1/participation/status` | NO | Participation badge |
| `GET /api/v1/demo/portfolio` | NO | Portfolio Teaser |

## Directory Structure

```
landing/
├── app/
│   ├── layout.tsx                 # Root layout: Inter font, metadata, JSON-LD
│   ├── page.tsx                   # Composes all sections
│   ├── globals.css                # Tailwind directives + brand palette
│   ├── sitemap.ts                 # SEO
│   └── robots.ts                  # SEO
├── components/
│   ├── hero/
│   │   ├── Hero.tsx               # Tagline, CTAs, gradient background
│   │   └── HeroChart.tsx          # Animated SVG line chart
│   ├── problem/
│   │   └── ProblemSection.tsx     # 3 pain-point cards
│   ├── how-it-works/
│   │   └── HowItWorks.tsx         # 3-step horizontal stepper
│   ├── live-demo/
│   │   ├── LiveDemo.tsx           # Container: chart + badges
│   │   ├── IliChart.tsx           # lightweight-charts integration
│   │   └── StatusBadges.tsx       # ILI/Regime/Anomaly/Participation badges
│   ├── signal-showcase/
│   │   ├── SignalShowcase.tsx      # Signal table
│   │   └── SignalStatusBadge.tsx  # Per-signal status badge
│   ├── portfolio-teaser/
│   │   └── PortfolioTeaser.tsx    # Demo portfolio card (placeholder)
│   ├── pricing/
│   │   └── PricingSection.tsx     # Open source card
│   ├── footer/
│   │   └── Footer.tsx             # Links + disclaimer
│   └── shared/
│       ├── Section.tsx            # Scroll-reveal wrapper (IntersectionObserver)
│       ├── Badge.tsx              # Reusable badge
│       └── Tooltip.tsx            # Accessible tooltip
├── lib/
│   ├── api.ts                     # fetchApi with timeout + mock fallback
│   ├── types.ts                   # TypeScript interfaces (match OpenAPI schemas)
│   └── mock-data.ts               # Static placeholder data for all sections
├── hooks/
│   └── useApiData.ts              # SWR wrapper with error/loading states
├── __tests__/
│   ├── components/                # 1+ test per component
│   └── lib/                       # API client fallback tests
├── public/
│   └── images/og-image.png        # OpenGraph 1200x630
├── next.config.ts                 # output: 'export', unoptimized images
├── vitest.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── postcss.config.mjs
├── package.json
└── .gitignore
```

## Implementation Phases

### Phase A: Scaffolding ✅ DONE

1. ✅ Create `landing/` with `npx create-next-app@latest` (TypeScript, Tailwind, ESLint, App Router)
2. ✅ Configure `next.config.ts`: `output: 'export'`, `images: { unoptimized: true }`, `trailingSlash: true`
3. ✅ Add production deps: `swr`, `lightweight-charts`
4. ✅ Add dev deps: `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`, `@vitejs/plugin-react`
5. ✅ Create `vitest.config.ts` with jsdom environment + `lightweight-charts` mock
6. ✅ Create `lib/types.ts` — TypeScript interfaces matching OpenAPI schemas + v5 plan fields
7. ✅ Create `lib/mock-data.ts` — static data for ILI history, signals, portfolio, statuses
8. ✅ Add `landing/.gitignore` (node_modules, .next, out, coverage)
9. ✅ Add `landing/node_modules/` and `landing/.next/` to root `.gitignore`
10. ✅ **Verify:** `npm run build && npm run lint && npm run typecheck` all pass — 5 type tests green

### Phase B: Static Sections

1. `components/shared/Section.tsx` — IntersectionObserver scroll-reveal, `prefers-reduced-motion`
2. `components/shared/Badge.tsx` — color-coded badge with aria-label
3. `components/shared/Tooltip.tsx` — accessible tooltip on hover/focus
4. `components/hero/Hero.tsx` — tagline, CTAs ("Start Demo" → app.tickonomics.io, "GitHub" → repo)
5. `components/hero/HeroChart.tsx` — inline SVG with CSS `@keyframes` draw animation
6. `components/problem/ProblemSection.tsx` — 3 cards: blind spots, lagging indicators, manual analysis
7. `components/how-it-works/HowItWorks.tsx` — 3-step stepper: Ingest → Analyze → Act
8. `components/pricing/PricingSection.tsx` — open source badge, GitHub link, self-host instructions
9. `components/footer/Footer.tsx` — links, disclaimer, copyright year
10. `app/layout.tsx` — Inter via `next/font/google`, full metadata (OG, Twitter Card), JSON-LD
11. `app/page.tsx` — compose all sections, inline metadata
12. **Verify:** `npm run build` produces `out/`, all sections visible at localhost:3000

### Phase C: Dynamic Sections

1. `lib/api.ts` — `fetchApi<T>(path)` with AbortController 5s timeout, mock fallback on error
2. `hooks/useApiData.ts` — SWR wrapper returning `{ data, error, isLoading }` with fallbackData
3. `components/live-demo/StatusBadges.tsx` — ILI (VALID/DEGRADED/DISLOCATED), Regime (4 states), Anomaly, Participation (ADMISSIBLE/SUPPRESSED)
4. `components/live-demo/IliChart.tsx` — `lightweight-charts` in useRef/useEffect, lazy-loaded via `next/dynamic`, responsive via ResizeObserver
5. `components/live-demo/LiveDemo.tsx` — composes chart + badges, uses SWR
6. `components/signal-showcase/SignalStatusBadge.tsx` — ACTIONABLE, SPECULATIVE_STALE_MACRO, COST_EXCEEDS_EXPECTED_MOVE, COOLDOWN, INSUFFICIENT_DATA
7. `components/signal-showcase/SignalShowcase.tsx` — responsive table with SWR
8. `components/portfolio-teaser/PortfolioTeaser.tsx` — mock card with "coming soon"
9. **Verify:** page loads with mock data, no console errors, fallback works with API down

### Phase D: SEO & Polish

1. `app/sitemap.ts` — single-page sitemap for tickonomics.io
2. `app/robots.ts` — allow all, link sitemap
3. `public/images/og-image.png` — placeholder OG image (1200x630)
4. JSON-LD `SoftwareApplication` in `app/page.tsx`
5. Responsive testing (mobile viewport)
6. `prefers-reduced-motion` verification
7. **Verify:** Lighthouse Performance > 90, Accessibility > 95, SEO > 95

### Phase E: Testing

1. Tests for static components: Hero, ProblemSection, HowItWorks, PricingSection, Footer
2. Tests for dynamic components: LiveDemo (loading/data/error states), SignalShowcase (empty/populated/error), StatusBadges (all status variants)
3. Tests for `lib/api.ts`: successful fetch, network error fallback
4. **Verify:** `npm run test -- --coverage` > 80% line coverage, all Given-When-Then structured

### Phase F: ADR & Documentation

1. Write `docs/adr/ADR-005-landing-page-architecture.md` (Context/Decision/Consequences)
2. Update `docs/implementation-plan.md` Track 6 status to DONE

## Key Files to Reuse/Reference

- `api-contracts/src/main/resources/openapi.yaml` — TypeScript interfaces must match `AlphaSignal`, `StrategyStatus` schemas
- `web/src/main/java/com/tickonomics/web/config/WebConfig.java` — CORS already allows cross-origin
- `web/src/main/java/com/tickonomics/web/controller/QuantController.java` — existing endpoints
- `docs/plan_v5/06-landing-page.md` — product spec with detailed validation criteria
- `docs/adr/ADR-002-file-based-overflow.md` — ADR format reference

## Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Spring Boot backend |
| `NEXT_PUBLIC_ANALYTICS_ID` | (empty) | Plausible/Umami (deferred) |

## Verification

1. `npm run build` — produces `out/` directory
2. `npm run typecheck` — zero TS errors
3. `npm run lint` — zero ESLint errors
4. `npm run test` — all tests pass, > 80% coverage
5. `npx lighthouse http://localhost:3000` — Performance > 90, A11y > 95, SEO > 95
6. Manual: all sections render on desktop + mobile
7. Manual: disconnect API → mock data renders, no errors
8. Manual: OpenGraph tags present in page source
9. Manual: `sitemap.xml` and `robots.txt` accessible
