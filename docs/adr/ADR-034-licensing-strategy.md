# ADR-034: Licensing Strategy for Commercialization

Date: 2026-06-17
Status: Accepted

## Context
Tickonomics is currently AGPL-licensed. To enable commercial monetization without violating dependencies, we adopt a hybrid strategy: immediate monetization via SaaS/support (Option 1) and preparation for future dual licensing via CLA (Option 2).

## Decision
1.  **Immediate:** Launch "Tickonomics Cloud" SaaS offering and paid support. The SaaS platform itself can remain closed-source, while the Tickonomics core remains AGPL.
2.  **Preparation:** Introduce a Contributor License Agreement (CLA) in `CONTRIBUTING.md` to ensure future code contributions can be dual-licensed commercially when necessary.
3.  **Future:** Evaluate dual licensing (commercial vs. AGPL-3.0) once enterprise demand is confirmed.

## Consequences
- Requires diligent separation of core (AGPL) and commercial (Proprietary) components.
- Contributors must accept the CLA, which might slightly increase the barrier for some community contributions.
- Provides a clear path to enterprise commercialization.
