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