# Structural / Architectural Gaps

| #   | Gap                                      | Plan Ref                | Note                                                                                          |
| --- | ---------------------------------------- | ----------------------- | --------------------------------------------------------------------------------------------- |
| 1   | `webflux/` subproject                    | `01-scaffolding.md` §12 | WebFlux adapter module for future reactive runtime — **not created** (intentionally deferred) |
| 2   | `analytics/` Gradle subproject with Java | `settings.gradle`       | Listed in `settings.gradle` but contains only Python, no Java analytics bridge code           |
| 3   | `integration-tests/` subproject          | `01-scaffolding.md`     | Module exists in `settings.gradle` with Testcontainers config but **no test classes written** |
