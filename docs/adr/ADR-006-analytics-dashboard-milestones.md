# ADR-006: Analytics Dashboard Milestone Breakdown

**Date:** 2026-05-30
**Status:** Accepted
**Context:** Track 7 — Analytics Dashboard

## Decision

Break the 40-component Analytics Dashboard into 12 sequential milestones documented in `docs/milestones/track7/`. Each milestone is a self-contained unit with clear dependencies, acceptance criteria, and data sources.

## Milestone Structure

| Phase | Milestones | Focus |
|-------|-----------|-------|
| Foundation | M1–M6 | Scaffolding, charts, KPIs, monitoring, grids, config |
| v4 Analytics | M7–M8 | Regime detection, governance, data quality |
| v5 Analytics | M9–M12 | Backtesting, microstructure, trading, statistical integrity |

## Dependency Strategy

- **M1** has zero dependencies — it's the foundation
- **M2–M6** depend only on M1 (parallelizable)
- **M7–M12** depend on 2–3 earlier milestones each (sequential after foundation)

This allows M2–M6 to be implemented in parallel if resources permit, while v4/v5 milestones build incrementally on completed foundation work.

## Technology Alignment

Dashboard aligns with the existing `landing/` patterns:
- Tailwind CSS v4 (`@tailwindcss/postcss`, no `tailwind.config.ts`)
- Vitest + React Testing Library (not Jest)
- ESLint 9 flat config
- `lightweight-charts` for price/ILI charting

Divergence from `landing/`:
- TanStack Query instead of SWR (richer cache invalidation for real-time data)
- Separate `frontend/` directory (not inside `landing/`)
- Perspective for high-density data grids (not used in landing)

## Consequences

- Each milestone is independently testable and deliverable
- v4/v5 features can be prioritized or deferred without blocking foundation work
- The milestone files serve as both implementation guides and acceptance checklists
