# CI/CD — 3 Missing Workflows

**Plan ref:** `09-cicd-pipeline.md`

| #   | Workflow             | Trigger                                | Description                                                                                    |
| --- | -------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 1   | `demo-report.yml`    | Weekly Monday 6:00 UTC                 | Queries demo DB for P&L/signal count/hit rate, posts GitHub Issue                              |
| 2   | `deploy-landing.yml` | Push to main with `landing/**` changes | Build and deploy landing page to Vercel/Cloudflare                                             |
| 3   | `chaos-tests.yml`    | Weekly Saturday 2:00 UTC               | 5 chaos scenarios: TSDB latency, worker restart, buffer overflow, WS disconnect, total failure |

> **Note:** The plan calls for 7 separate workflows; the implementation has 4 (with `chaos-benchmark.yml` merging chaos + benchmark). The `benchmark.yml` as a standalone is also missing but partially covered by `chaos-benchmark.yml`.
