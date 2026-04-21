# ADR-005: Landing Page Architecture

**Date:** 2026-05-30
**Status:** Implemented

## Context

Track 6 requires a production-ready marketing landing page at `tickonomics.io`. The repository is a Java/Spring Boot monorepo with zero Node.js tooling. The backend has 7 API endpoints (mostly returning stubs), and only 2 of 6 required landing page endpoints exist (both returning empty data). The page must achieve Lighthouse Performance > 90, Accessibility > 95, and SEO > 95.

## Decision

Build a standalone Next.js 16 static-export marketing page in `landing/`, completely independent of the Java build system. Key architectural choices:

1. **Static export (`output: 'export'`):** Produces a CDN-deployable static site with no Node runtime. Highest Lighthouse scores, zero server cost, and no SSR complexity.

2. **Tailwind CSS v4:** Smallest CSS bundle via `@tailwindcss/postcss`. Custom brand palette (`ili-green`, `ili-amber`, `ili-red`, `ili-blue`) defined with `@theme inline` directives.

3. **lightweight-charts v5 (TradingView):** Purpose-built for financial data at 45KB gzipped. Dynamically imported via `next/dynamic` with `ssr: false` to avoid SSR issues.

4. **CSS animations + IntersectionObserver:** Zero-JS scroll-reveal via `@keyframes` and `IntersectionObserver`. `prefers-reduced-motion` respected. No framer-motion (saves 30KB).

5. **SWR with mock fallback:** Every dynamic component fetches from the Spring Boot API via `fetchApi<T>()` with 5s AbortController timeout. On any error (network, HTTP, timeout), static mock data is substituted. SWR provides stale-while-revalidate UX compatible with static export.

6. **Vitest + React Testing Library:** Tests run in jsdom with `IntersectionObserver` and `matchMedia` polyfills. All 38 tests follow Given-When-Then structure. Coverage: 92% lines.

7. **Not added to `settings.gradle`:** The `landing/` directory is a standalone Node.js project. The Java build ignores it entirely.

## Consequences

- **Decoupled deployment:** Landing page can be deployed to Vercel/Cloudflare Pages independently of the backend
- **Graceful degradation:** All sections render with mock data when the API is unavailable — no broken UI
- **High Lighthouse scores:** Static export + zero-runtime animations + Tailwind CSS purging yields minimal JS/CSS payloads
- **API gap tolerance:** When backend endpoints are implemented, the landing page automatically switches from mock to live data with no code changes
- **Testing overhead:** jsdom lacks `IntersectionObserver` and `matchMedia`, requiring polyfills in `vitest.setup.ts`
- **Static export limitations:** No server-side features (API routes, ISR, middleware). SEO files (`sitemap.ts`, `robots.ts`) require `export const dynamic = "force-static"`
