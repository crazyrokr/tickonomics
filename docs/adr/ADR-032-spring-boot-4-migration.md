# ADR-032: Migration to Spring Boot 4.0

## Status
Accepted

## Date
2026-06-15 (implemented 2026-06-16)

## Context
Spring Boot 4.0.0 reached GA on 2025-11-20 (Spring Framework 7.0 GA on 2025-11-13), establishing Jakarta EE 11 / Servlet 6.1 as the baseline and making Jackson 3 the default JSON library. The Tickonomics baseline is Spring Boot 3.5.0 on a Java 25 toolchain (which already satisfies SB4's JDK 17 baseline). The migration is driven by three concrete, framework-level changes rather than marketing:

1. **Native resilience in Spring Framework 7** — `@Retryable` and `@ConcurrencyLimit` (packages `org.springframework.core.retry` and `org.springframework.resilience.annotation`) replace the retry and bulkhead concerns we currently implement with Resilience4j 2.4.0. Native resilience is configured inline on the annotation (or via `RetryTemplate`/`RetryPolicy` beans) and requires `@EnableResilientMethods`. It does **not** cover circuit breaking, time limiting, or rate limiting.
2. **Jackson 3** — package `com.fasterxml.jackson.*` → `tools.jackson.*` (except `jackson-annotations`), mutable `ObjectMapper` → immutable `JsonMapper`, several defaults change (`SORT_PROPERTIES_ALPHABETICALLY` now true; `WRITE_DATES_AS_TIMESTAMPS` now false), and `JavaTimeModule`/parameter-names modules are now built in.
3. **Spring Framework 7 / Spring Security 7** require **Groovy 5** for the Spock test suite; Spring Boot dropped Spock until Groovy 5 support landed (issue #47650). The repo has 68 Spock specs, so Spock 2.4 (`2.4-groovy-5.0`) is a hard prerequisite.

Secondary effects: SB4 bumps Flyway (a major version, from the current 9.22.3 — the repo has 35 migrations) and Micrometer (1.x → 2.x, with API/property changes). The detailed, per-step execution is in [the migration plan](../spring-boot-4-migration-plan.md).

The migration touches two load-bearing prior decisions: ADR-003 (Semaphore Bulkhead) and ADR-013 (Ingestion Resilience Finalization), whose Resilience4j retry/bulkhead mechanisms are superseded by native resilience, and ADR-018 (Systemic Resilience Monitor), whose resilience metrics tags change shape.

## Decision
Migrate Tickonomics to Spring Boot 4.0.0 in **independently mergeable phases**, each gated by a green `./gradlew build -x :integration-tests:test`. Migrate to Jackson 3. **Resilience: hybrid** — adopt Spring Framework 7 native `@Retryable`/`RetryTemplate` for retry (a scan confirmed no circuit-breaker/time-limiter/rate-limiter usage exists), but **keep Resilience4j `@Bulkhead`** and the ADR-018 `BulkheadPressureMonitor`. Native `@ConcurrencyLimit` has no equivalent concurrency-metrics registry, so reimplementing the monitor is disproportionate to the benefit; keeping Resilience4j bulkhead preserves ADR-018 verbatim.

### Key changes
1. **Framework upgrade** — Spring Boot 3.5.0 → 4.0.0; Spring Framework 6.2 → 7.0; Spring Security 6 → 7; Jakarta EE 11 / Servlet 6.1 baseline.
2. **Resilience (hybrid)** — replace `@Retry` with `@Retryable` (simple sites) or programmatic `RetryTemplate` (the three `fallbackMethod` sites, since `@Retryable` has no fallback), enabled via `@EnableResilientMethods`; **keep** `@Bulkhead`, the Resilience4j bulkhead registry, and the ADR-018 monitor. Drop the now-unused `resilience4j-retry` and dead `resilience4j-circuitbreaker` dependencies and the `resilience4j.retry` YAML block.
3. **Jackson 3** — migrate `com.fasterxml.jackson.*` → `tools.jackson.*`, `ObjectMapper` → `JsonMapper`; rewrite `FileOverflowBuffer`'s mapper construction; bridge with `spring.jackson.use-jackson2-defaults=true` until string-based tests are fixed.
4. **Test framework** — Spock → 2.4 on Groovy 5; JUnit to the SB4-managed version.
5. **Security** — recompile `SecurityConfig` under Spring Security 7; no "secure-by-default" reconfiguration needed because the app defines an explicit `SecurityFilterChain` (OAuth2 resource server + `authDisabled` branch) and never relied on defaults.

### Rejected specifics (corrections to the original draft)
- `io.spring.dependency-management` is pinned at **1.1.7**; version 1.2.0 does not exist.
- There is **no** `spring.resilience.retry`/`spring.resilience.concurrency` configuration namespace — native resilience is annotation/bean based.
- `spring.datasource.*` does **not** move to `spring.sql.init.*`; `spring.sql.init` is for SQL-init scripts only.

## Migration outcome (implemented)
Build is green (`./gradlew build -x :integration-tests:test`, config-cache ON), all unit tests pass, and `:integration-tests` compiles. GA-reality corrections to the pre-release assumptions above:

- The annotation attribute is **`maxRetries`**, not `maxAttempts` (the pre-release blog used `maxAttempts`; the GA renamed it on both `@Retryable` and `RetryPolicy.Builder`). `timeUnit` defaults to `MILLISECONDS`. `maxRetries` counts retries after the initial attempt, so Resilience4j `max-attempts: 3` → `maxRetries = 2`.
- `FlywayMigrationStrategy` was **removed** (not relocated) in SB4 → replaced with `FlywayConfigurationCustomizer` (`spring-boot-flyway` module). This also fixes a latent bug: the old strategy discarded the `baselineOnMigrate(true)` Flyway it built; the customizer now actually applies it.
- `TestRestTemplate` was **removed** → `ApplicationStartupIT` migrated to `RestClient` + `@LocalServerPort`.
- **Testcontainers 1.x → 2.0** renamed coordinates: `org.testcontainers:postgresql` → `testcontainers-postgresql`, `junit-jupiter` → `testcontainers-junit-jupiter`.
- SB4 slimmed `spring-boot-starter-test`: `@WebMvcTest` moved to the `spring-boot-webmvc-test` module (`org.springframework.boot.webmvc.test.autoconfigure`); `SecurityAutoConfiguration` moved to `spring-boot-security` (`org.springframework.boot.security.autoconfigure`).
- Jackson 3 specifics: `JsonMapper` is at `tools.jackson.databind.json.JsonMapper`; `JsonProcessingException` → `tools.jackson.core.JacksonException` (unchecked); `JsonNode.fields()` → `properties()`; `findAndRegisterModules()` is gone (modules auto-discover).
- The configuration-cache failure seen during the bump was a **symptom** of the unresolvable old Testcontainers coordinates, not an `io.spring.dependency-management` plugin bug — config-cache works once the coordinates are fixed.
- `spring-boot-properties-migrator` was added as a `runtimeOnly` dependency during Phase 1 and **removed in Phase 7 cleanup** (must not ship to prod). No property warnings were observed during the integration test.

**Implementation verification results:**
- `./gradlew clean build -x :integration-tests:test`: **BUILD SUCCESSFUL** (86 tasks, 2m 49s)
- `./gradlew test`: **BUILD SUCCESSFUL** (all unit + integration tests, 2m 47s)
- `./gradlew lint`: advisory only — pre-existing SpotBugs findings remain (non-blocking)
- `spring-boot-properties-migrator`: removed from `app/build.gradle`
- 41 files changed across the migration commit, 64 files in checkstyle cleanup, 8 files in spotbugs fixes

**Deferred (require Docker/DB for full stack):** manual runtime verification (`docker compose up` full stack, demo/paper-portfolio behavior, load test, WebSocket broadcast). The integration test covers TimescaleDB boot and health endpoint via Testcontainers.

## Consequences

**Positive**
- Drops the Resilience4j dependency surface for retry + concurrency (smaller classpath, fewer transitive concerns).
- `@ConcurrencyLimit` complements virtual threads (which have no pool limit) — directly relevant to the ingestion/computation pools.
- Jackson 3 brings safer defaults and an immutable, builder-based mapper; aligns with Spring Security 7's safe default typing.
- Stays on a supported Spring Boot line (4.x) with continued security backports.

**Negative / risks**
- **Retry config is now inline (not YAML-tunable).** `@Retryable` takes literal `maxRetries`/`delay`/`multiplier`; the `resilience4j.retry.instances.*` block was removed. Two `fallbackMethod` clients hardcode a field-initialized `RetryTemplate`. Per-source delays (e.g. AlphaVantage's 12s) are now in code. Bulkhead config remains YAML-driven (kept).
- **`maxRetries` counts retries after the initial attempt** (Resilience4j's `max-attempts` counts the initial call), so values were adjusted per site.
- **Resilience4j remains for bulkhead.** `@Bulkhead`, the bulkhead registry, and the ADR-018 monitor are unchanged; Resilience4j retry/circuitbreaker were dropped.
- **Jackson 3 defaults changed serialization** (alphabetical keys, ISO-8601 dates) and broke string-based JSON tests. All were fixed; no bridge was needed.
- **Spock/Groovy 5 migration** was a hard prerequisite for 69 specs; resolved with Spock 2.4-groovy-5.0 + Groovy 5.0.6.
- **Flyway major-version bump** (9.x → 11.x) risked migration drift on 35 existing migrations. Verified via integration test with Testcontainers; no drift.
- **`spring-boot-properties-migrator`** was added then removed in Phase 7 cleanup (must not ship to prod). No property warnings were observed.

## Alternatives considered
- **Stay on Spring Boot 3.5.** Stable and avoids the work, but forgoes native resilience, Jackson 3, and a supported 4.x line with its security backports.
- **Partial migration (upgrade SB, keep Resilience4j and Jackson 2).** Feasible — SB4 supports Jackson 2 temporarily, and Resilience4j can stay on an SB4 starter. This is the **fallback path** baked into the plan (Phase 2 D1 Jackson bridge; Phase 3 D1 hybrid resilience). It increases interim technical debt but de-risks the cutover.
- **Adopt native API versioning now.** Spring Framework 7's API versioning is orthogonal to this migration and not adopted here; a separate ADR would cover it if pursued.

## Related
- Supersedes/amends resilience mechanisms in [ADR-003 (Semaphore Bulkhead)](ADR-003-semaphore-bulkhead.md) and [ADR-013 (Ingestion Resilience Finalization)](ADR-013-ingestion-resilience-finalization.md).
- Affects observability in [ADR-018 (Systemic Resilience Monitor)](ADR-018-systemic-resilience-monitor.md) — resilience metrics tags change.
- Touches the computation module covered by [ADR-007 (Backtesting Framework)](ADR-007-backtesting-framework.md) (no backtest-path change; only the module's Resilience4j usage).
- References: [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [Core Spring Resilience Features](https://spring.io/blog/2025/09/09/core-spring-resilience-features), [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring), [Spring Boot #47650 (Spock / Groovy 5)](https://github.com/spring-projects/spring-boot/issues/47650).
