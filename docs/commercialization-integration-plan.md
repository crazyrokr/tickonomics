# Tickonomics Integration Plan: Commercialization Roadmap

## Objective
Integrate the provided 5-phase commercialization roadmap into the existing project to transform Tickonomics from a research platform to a B2C product.

## Assessment of Current State
- **Infrastructure:** Multiple `docker-compose` files exist. Phase 0.1 is straightforward.
- **Frontend:** Existing `frontend/` (Next.js) is suitable for UI adaptation (Phase 1).
- **Backend:** `app/` and `computation/` are set up. `BacktestEngine` exists.
- **Monetization:** No existing payment integration (Phase 3).

## Phased Integration Plan

### Phase 0: Infrastructure & Compliance (1 Week)
- [ ] **[INFRA] Create `docker-compose.freemium.yml`**
    - *Goal:* Provide a resource-constrained environment for free-tier users.
    - *Action:* Copy `docker-compose.prod.yml` and apply limits (backend: 256MB, timescaledb: 1GB). Disable non-essential services (Jaeger/Loki).
- [ ] **[JAVA] Implement Anonymized Usage Monitoring**
    - *Goal:* Track feature adoption without collecting PII.
    - *Action:* Create `UsageMonitorService` in `app` using Micrometer to increment counters on key API calls.
- [ ] **[DOCS] Draft dual-licensing strategy document**
    - *Goal:* Formalize AGPL vs. Commercial split.
    - *Action:* Define module ownership and proprietary boundary in `docs/LICENSING_STRATEGY.md`.

### Phase 1: Frontend Adaptation (2-3 Weeks)
- [ ] **[NEXTJS] Redesign landing page for ticker input**
    - *Goal:* Direct users to a high-impact search/entry point.
    - *Action:* Repurpose `frontend/app/page.tsx` as a search landing and move current dashboard to `/dashboard`.
- [ ] **[NEXTJS] Create "Asset Inspector" component**
    - *Goal:* Modular "at-a-glance" asset analysis view.
    - *Action:* Implement `frontend/components/inspector/AssetInspector.tsx` integrating charts and risk metrics.
- [ ] **[NEXTJS] Simplify navigation and route non-core tools**
    - *Goal:* Prioritize B2C tools in the UI.
    - *Action:* Refactor `Sidebar.tsx` to group research tools under "Advanced".

### Phase 2: Core B2C Features (3-4 Weeks)
- [ ] **[JAVA] Unified Risk Calculator API**
    - *Goal:* Expose complex risk logic via simple ticker-based REST endpoint.
    - *Action:* Create `RiskCalculatorController.java` wrapping `BehaviouralRiskProcessor` and `ClimateRiskGuard`.
- [ ] **[JAVA/PYTHON] Build Compliance Filter service**
    - *Goal:* Contextual news alerts for asset views.
    - *Action:* Create `ComplianceFilterService.java` leveraging existing `fed-rss` and `news` modules.
- [ ] **[NEXTJS] Build Strategy Sandbox UI**
    - *Goal:* Simplified "What-if" scenario runner for retail users.
    - *Action:* Implement `frontend/app/sandbox/page.tsx` utilizing the `BacktestEngine`.

### Phase 3: Monetization (2-3 Weeks)
- [ ] **[JAVA] Integrate payment gateway (Stripe/Paddle)**
    - *Goal:* Handle subscriptions and one-time payments.
    - *Action:* Implement `PaymentService.java` with webhook listeners for payment events.
- [ ] **[JAVA] Define and implement user tiers in backend**
    - *Goal:* Enforce limits and feature access.
    - *Action:* Update `SecurityConfig.java` with `ROLE_FREE`/`ROLE_PRO` and implement `RateLimitInterceptor`.

### Phase 4: Packaging (1-2 Weeks)
- [ ] **[SHELL] Create installer scripts**
    - *Goal:* Automated setup for self-hosted instances.
    - *Action:* Write `scripts/install_tickonomics.sh` to validate environment and deploy compose stacks.
- [ ] **[DOCS] Update documentation and onboarding**
    - *Goal:* Clear path for new commercial/retail users.

### Phase 5: Launch (Ongoing)
- [ ] Pre-launch, public launch, and feedback loops.

## Verification & Testing
- Every phase requires updating/adding integration tests.
- UI changes require E2E tests (Playwright is already used in `frontend/`).
- Backend changes require unit tests in `computation/` and `app/`.
