# ADR-028: Auto-install git hooks via a first-party Gradle plugin

**Date:** 2026-06-15
**Status:** Accepted
**Related:** ADR-026 (Local GitHub Actions Verification)

## Context

ADR-026 ships version-controlled git hooks (`scripts/git-hooks/{pre-commit,pre-push}`)
installed by `scripts/install-git-hooks.sh` (the `make install-hooks` target). That works,
but it depends on a developer remembering to run it after clone — a fresh checkout has no
hook protection until that one manual step happens, and the hooks never refresh if the
source files change unless the step is re-run.

Three third-party Gradle git-hook plugins were evaluated to remove that manual step
(`DanySK/gradle-pre-commit-git-hooks`, `STAR-ZERO/gradle-githook`,
`jakemarsden/git-hooks-gradle-plugin`). Only `DanySK` is actively maintained (last push
2026-06-13); the other two have been dormant since late 2020 and are a configuration-cache
risk on Gradle 9. `DanySK` is also a settings plugin designed for `settings.gradle.kts`,
which would force this Groovy-DSL repo onto the "cumbersome" Groovy bridge or a Kotlin
migration — a poor trade for what is, mechanically, a two-file copy.

The load-bearing constraint is `org.gradle.configuration-cache=true`. The two patterns that
would give "run on every invocation" — `gradle.taskGraph.whenReady { … }` and
`gradle.buildFinished { … }` — are both configuration-cache-hostile (the former captures
non-serializable graph state; the latter is deprecated). Any solution must therefore be a
declarative task wired onto a lifecycle task at configuration time.

## Decision

Auto-install the hooks with a **first-party `buildSrc` plugin** (`io.tickonomics.git-hooks`,
no third-party dependency) that registers an `installGitHooks` `Copy` task and wires it onto
`build`.

- The task copies `scripts/git-hooks/*` into `.git/hooks` and sets mode `0755`
  (`eachFile { it.mode = 0755 }`), so the executable bit does not depend on the source mode.
- It is wired via `plugins.withId('base') { tasks.named('build').dependsOn(installTask) }`,
  so any project applying `java` (which applies `base`) — including the root — installs the
  hooks on `./gradlew build`.
- Everything is declarative and resolved at configuration time (`layout.projectDirectory.dir(...)`,
  no providers capturing `Project`, no `whenReady`/`buildFinished`), so it is configuration-cache
  compatible.

This achieves the practical goal — clone → first `./gradlew build` → hooks installed and kept
current — without a plugin dependency. It does **not** give the "before literally any task /
on IDE import" guarantee that a settings plugin like `DanySK` offers; that narrower
automaticity is the deliberate trade for zero third-party surface.

`scripts/install-git-hooks.sh` and `make install-hooks` are retained as the explicit
bootstrap path for installing hooks before the first build (chicken-and-egg: the task only
runs once a build runs).

## Consequences

- Hooks are installed and refreshed automatically on every `./gradlew build`; `Copy` is
  incremental, so a warm build skips the task (up-to-date).
- `build.gradle` gains one line (`id 'io.tickonomics.git-hooks'`) and a `buildSrc/` module.
  The hook sources, the bootstrap script, the Makefile targets, and the regression tests
  from ADR-026 are unchanged — this layers on top, it does not replace them.
- The task overwrites `.git/hooks/{pre-commit,pre-push}` on each build; version-controlled
  sources are the source of truth, same as the bootstrap script. A developer who hand-edits
  a live hook will have it overwritten on the next build — they should edit the source.
- Worktrees (where `.git` is a file, not a directory) are not handled, matching the
  pre-existing limitation of `install-git-hooks.sh`.
- Unit-tested with GradleRunner against a throwaway project (see
  `buildSrc/src/test/.../GitHooksPluginSpec`): copy + executable bit, up-to-date on re-run,
  re-copy on source change, dependency on `build`, and configuration-cache compatibility.

## Alternatives considered

- **Third-party plugin (`DanySK`).** Rejected for this repo: forces a `settings.gradle.kts`
  migration or the unsupported Groovy bridge, and adds a dependency for a two-file copy.
  It is the right choice if "install on IDE import" later becomes a hard requirement.
- **`STAR-ZERO` / `jakemarsden`.** Rejected: dormant since 2020, configuration-cache risk on
  Gradle 9; `jakemarsden` additionally emits only `./gradlew <task>` bodies and cannot express
  the actionlint/act shell logic.
- **Task inlined directly in `build.gradle`.** Workable, but not unit-testable in isolation:
  testing a task defined in the root script requires running against the real multi-module
  build (mutating the real `.git/hooks` or paying full-build configuration cost). Putting the
  logic in a `buildSrc` plugin lets GradleRunner test it against a tiny throwaway project.
- **`gradle.taskGraph.whenReady` / `buildFinished`.** Rejected: configuration-cache-hostile.
