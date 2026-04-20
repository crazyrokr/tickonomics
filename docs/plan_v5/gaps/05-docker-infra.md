# Docker & Infrastructure — 3 Missing Items

**Plan ref:** `11-deployment-operations.md`

| #   | Item                                       | Description                                                                                                              |
| --- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| 1   | `docker-compose.openbb.yml`                | OpenBB Platform sidecar for equity prices                                                                                |
| 2   | Chronicle Queue volume in docker-compose   | Dedicated NVMe/tmpfs volume for ingestion overflow                                                                       |
| 3   | **Spring Security / OAuth2 config** (Java) | Plan requires OAuth2+PKCE, all `/api/**` require JWT, HTTPS, CSP headers — no Spring Security dependency or config found |

> **Note:** Jaeger **is** present in `docker-compose.yml`. The landing page Dockerfile **does** use nginx (matching the plan). Backend Dockerfile **does** use multi-stage build.
