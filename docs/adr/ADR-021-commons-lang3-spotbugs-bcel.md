# ADR-021: Pin commons-lang3 to 3.20.0 project-wide for SpotBugs/BCEL compatibility

**Date:** 2026-06-14
**Status:** Accepted

## Context

`./gradlew clean build` logged a repeating `ClassNotFoundException: org.apache.commons.lang3.Strings` during every module's `spotbugsMain`/`spotbugsTest` task. The exception did not fail the build because `spotbugs { ignoreFailures = true }` is set, but it left SpotBugs analysis silently broken (incomplete) across cdm, api-contracts, computation, app, persistence, ingestion, and web.

Root cause, verified end-to-end:

- SpotBugs engine 4.10.2 (resolved by the `com.github.spotbugs` plugin 6.5.6) pulls BCEL 6.12.0, whose `org.apache.bcel.generic.Type.internalTypeNameToSignature` references the class `org.apache.commons.lang3.Strings`. That class exists only in commons-lang3 >= 3.19/3.20.
- The `io.spring.dependency-management` plugin is applied to every subproject with the Spring Boot 3.5.0 BOM (`spring-boot-dependencies:3.5.0`), which pins `commons-lang3` to 3.17.0. The managed version propagates into all configurations, including SpotBugs's own `spotbugs` engine configuration, overriding the 3.20.0 BCEL declares (`3.20.0 -> 3.17.0`).
- commons-lang3 3.17.0 has no `Strings` class, so BCEL throws `NoClassDefFoundError` -> `ClassNotFoundException` at analysis time. 3.20.0 was never downloaded because resolution went straight to 3.17.0.

No project source or build file references `commons-lang3` or `Strings` directly; this is purely a transitive version conflict inside the SpotBugs toolchain.

## Decision

Override the managed `commons-lang3` version to 3.20.0 for the whole project via the root `dependencyManagement` block in `build.gradle`:

```groovy
dependencies {
  dependency 'org.apache.groovy:groovy:4.0.29'
  dependency 'org.apache.commons:commons-lang3:3.20.0'
}
```

This is a project-wide override rather than a SpotBugs-configuration-scoped `resolutionStrategy.force`, chosen for simplicity and because commons-lang3 is a minor-version-compatible bump (3.17.0 -> 3.20.0) that is safe for the Spring Boot 3.5.0 runtime as well.

## Consequences

- SpotBugs analysis runs to completion with no `Strings` `ClassNotFoundException`; BCEL and the application runtime both resolve commons-lang3 3.20.0.
- The whole application now runs on commons-lang3 3.20.0 instead of Spring Boot 3.5.0's default 3.17.0. This is additive and binary-compatible at the API surface the project uses; revisit if a future Spring Boot upgrade tightens its commons-lang3 expectations.
- If a later SpotBugs/BCEL release drops the `Strings` dependency, this override can be removed to re-inherit the Spring Boot BOM version.
