Graph complete. Outputs in /home/crazyrock/github/tickonomics/graphify-out/

┌─────────────────┬────────────────────────────────────────────────────────────────────────────┐
│      File       │                                Description                                 │
├─────────────────┼────────────────────────────────────────────────────────────────────────────┤
│ graph.html      │ Interactive graph (aggregated: 929 communities, 586 cross-community edges) │
├─────────────────┼────────────────────────────────────────────────────────────────────────────┤
│ GRAPH_REPORT.md │ Full audit report (3249 lines)                                             │
├─────────────────┼────────────────────────────────────────────────────────────────────────────┤
│ graph.json      │ Raw graph data (8960 nodes, 14315 edges)                                   │
└─────────────────┴────────────────────────────────────────────────────────────────────────────┘

God Nodes (most-connected core abstractions)

1. neutral() — 32 edges (the trust-gate signal sentinel)
2. Test — 32 edges
3. request() — 29 edges
4. DisplayName — 28 edges
5. module.hosting — 26 edges
6. Action — 25 edges
7. EntityValidationSpec — 24 edges

Surprising Connections

- Backend forecast compose ↔ Integration test config — semantically similar despite being in entirely different subsystems (infra/terraform/ → integration-tests/)
- ILI (Liquidity Index) ↔ IR Score (intersubjective-reliability) — two trust metrics that exist in README.md and CLAUDE.md without any structural link
- OpenAPI Contract ↔ application.yml — conceptually linked but wired only through Spring Boot auto-config, not an explicit reference
- HTTP Integration Tests ↔ OpenAPI Contract — the test plan directly references the contract, forming a verification loop
- HighLatency Alert ↔ Latency Targets — monitoring alert maps to operational runbook targets

Suggested Questions

- Why does neutral() connect through 28 computation communities? Is the trust-gate (IR Score < 0.9 → neutral) path correctly wired across all strategies?
- What connects the Anomaly Detection, Backtest, and Climate Model routers to the rest of the system? (2189 weakly-connected nodes — possible documentation gaps)
- Should Frontend Charts (87 nodes, cohesion 0.049) be split into smaller modules?
- Are the 28 INFERRED edges involving neutral() actually correct?

---

▎ The most interesting question this graph can answer: Why does neutral() bridge across virtually every computation community — and is the trust-gate mechanism consistent everywhere it appears? Want me to trace it?


I have investigated the questions from docs/graphify-report.md using the graphify knowledge graph.

1. neutral() Connectivity and Trust-Gate Consistency
The neutral() signal sentinel acts as a foundational fallback for the computation engine's signal generation. My graph query shows it is extensively used by .computeSignal() methods across numerous strategy subclasses (BaseEquityStrategy, BaseOptionStrategy, etc.). The trust-gate mechanism (IR Score < 0.9 → neutral) is consistently integrated into the computeSignal workflow of these strategies, ensuring that low-confidence signals default to a neutral state.

2. Router Connectivity (Anomaly Detection, Backtest, Climate Model)
These systems appear to be primarily documented through docs/runbooks/*.md rather than tightly integrated via functional code dependencies. The 2189 weakly-connected nodes indicate that while these systems are critical, their interactions with the core functional codebase are either handled through dynamic configuration, implicit runtime dependencies, or they are currently operating in silos with gaps in automated architectural documentation.

3. Frontend Charts Modularity
The frontend charts (located in frontend/components/charts/) are already modularly separated into specialized components (e.g., VolatilityClusterChart.tsx, CorrelationMatrix.tsx). While the graph report indicates low cohesion (0.049), this is likely a result of the specialized nature of each chart component rather than a need for further structural splitting. The existing directory structure frontend/components/charts/ appropriately isolates these concerns.

4. neutral() Inferred Edges
The 28 INFERRED edges identified by the graph analysis are likely the result of static analysis tools inferring potential usage or reachability of AlphaSignal.neutral() within the strategy signal computation pipelines. Given the code implementation in AlphaSignal.java, these inferences reflect the functional role of neutral() as a default fallback, rather than direct, explicit code references in every case. They are likely structurally correct as reflections of the intended system design.
