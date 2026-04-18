# Proposal: Pedagogical Insights for Finance Preparedness

**Paper:** `pdf-ssrn/ssrn-3298186.pdf` ("Quantitative Skill and Introductory Finance: Does Ability Dominate Attitude?"
by Matthew M. Ross and A. Michelle Wright)

## Overview

This study examines the relative importance of mathematical ability (ACT Math, GPA) and attitudes toward math (measured
by ATMI and LIWC "work" words) in determining student preparedness for introductory finance courses.

## Applicability to Tickonomics

While this paper focuses on business education pedagogy, it provides valuable insights for the "Human-Algorithmic
Alignment" pillar of the Tickonomics project:

1. **Metric-Based Assessment:** The use of ATMI (Attitudes Toward Mathematics Inventory) and LIWC (Linguistic Inquiry
   and Word Count) offers a methodology for quantifying "human" readiness or aptitude for complex financial tasks.
2. **Ability vs. Attitude:** The finding that attitudes (quantified by LIWC "work" word usage) are robust predictors of
   performance, often outperforming traditional proxies like GPA, suggests that our system’s user-facing components (
   e.g., trader toxicity monitoring or algorithmic intensity dashboards) should potentially account for human
   psychological states (attitudes, stress, readiness).
3. **Data-Driven Intervention:** The paper highlights that students are often underprepared for quantitative rigor
   despite having passed prerequisites. Our system could provide "just-in-time" pedagogical feedback or guardrails for
   users whose engagement patterns suggest lack of quantitative readiness or high-stress, low-preparedness states.

## Proposal

- **Incorporate as Reference for Human Guardrails:** Use these metrics and insights when designing the "Human-Bias
  Mitigation" and "Auditability and Guardrails" modules in the Tickonomics pipeline.
- **Reference in Proposals:** Cite this research when proposing features that mitigate human decision-making biases (
  e.g., in `AI_TRADER_INSIGHTS_PROPOSALS.md`).
- **Archive:** Move this file to `docs/proposals/processed/` as it does not require direct implementation but serves as
  a key reference for user-facing algorithmic design.

## Status

- [ ] Paper analyzed.
- [ ] Proposal created.
- [ ] **Action:** Move `ideas/ssrn-3298186.pdf` to `docs/proposals/processed/`.
