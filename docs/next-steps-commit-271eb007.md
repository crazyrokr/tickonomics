# Next Steps — commit `271eb007` review

Review of commit `271eb007`: the Terraform `aws-forecast`/`aws-production` split, the
`bootstrap` state-backend stack, the multi-arch TA-Lib native loader, and the CI/Atlantis + ADR/runbook
changes. Findings below are reframed as implementation next steps, ordered by priority.

**Verified against repo state 2026-09-06** (`develop` @ `271eb007`, `origin/develop` @ `f8de1a18`):
all 9 findings confirmed accurate. Two corrections from that pass:
- **Item 6's original fix is wrong** — `@ConditionalOnResource` uses AND semantics (all listed
  resources must exist), so the multi-`.so` array would disable the bean in *every* build. Replaced.
- **Item 8's amend is safe** — the commit is unpushed (`origin/develop` is one behind), so no
  force-push is needed; do it before pushing.

Work top to bottom. **Item 1 blocks a green CI on the arm64 runner and should land first.**

---

## P0 — blocks CI

- [ ] **1. Make the TA-Lib loader spec portable to arm64.**
  - Where: `computation/src/test/groovy/com/tickonomics/computation/talib/TalibNativeLoaderSpec.groovy:104-119`
  - Problem: `"resolveLibraryResource falls back to generic .so when arch-specific missing"` hardcodes
    `System.setProperty('os.arch', 'aarch64')` and asserts the result is the generic `/libta-lib.so`.
    That is only true when `libta-lib-aarch64.so` is **absent** from the classpath — i.e. on an x86_64
    host. The `java-build-test-arm64` job (`pr-checks.yml:87-132`) runs on `ubuntu-24.04-arm`, where
    `setupTalib` builds and `copyTalibNative` (`computation/build.gradle:76-82`) places **both**
    `libta-lib.so` and `libta-lib-aarch64.so` at the classpath root, so `resolveLibraryResource()`
    returns `/libta-lib-aarch64.so` and the assertion fails → **the arm64 job is red.** (Verified:
    the x86_64 job passes only because `libta-lib-aarch64.so` is absent there.)
  - Fix: pick an arch whose `.so` is provably absent from the classpath before asserting, and **skip
    the test if both arch `.so` files are present** (the test cannot then prove absence):
    ```groovy
    def bothPresent = TalibNativeLoader.class.getResource('/libta-lib-aarch64.so') != null
        && TalibNativeLoader.class.getResource('/libta-lib-x86_64.so') != null
    if (!bothPresent) {
      def fakeArch = TalibNativeLoader.class.getResource('/libta-lib-aarch64.so') == null
          ? 'aarch64' : 'x86_64'
      System.setProperty('os.arch', fakeArch)
      ...
      resource == '/libta-lib.so'
    }
    ```
    (or gate with `@IgnoreIf { bothPresent }`, or make `resolveLibraryResource` testable by
    parameterizing arch + an availability predicate — that variant touches production code and needs
    its own Given-When-Then coverage).
  - Verify: `./gradlew :computation:test --tests "com.tickonomics.computation.talib.TalibNativeLoaderSpec"`
    passes on **both** an x86_64 and an aarch64 runner.

## P1 — logic / correctness

- [ ] **2. Stop declaring outputs `buildTalibNative` never produces.**
  - Where: `computation/build.gradle:27-30`
  - Problem: `outputs.files(libta-lib.so, libta-lib-x86_64.so, libta-lib-aarch64.so)` — only the build
    host's arch `.so` is ever created, so one declared output is always missing. Gradle requires all
    declared outputs to exist for up-to-date, so the task re-runs `cmake` + `make` on **every** build
    (even with a warm `native-libs` cache). **Impact is worse than it looks:** `buildTalibJna`
    (line 58) depends on `buildTalibNative` and runs `mvn clean install`, so every build re-runs
    cmake + make **and** the Maven build. (The arm64 CI job already mitigates this via `actions/cache`
    on `native-libs`; local dev and the x86 job do not.)
  - Fix (pick one):
    - declare only `libta-lib.so` + `libta-lib-<host-arch>.so`, arch computed at configuration time
      (map `os.arch` exactly as `detectArchSuffix` does — config-cache safe, evaluated once on the
      build host), or
    - declare only `libta-lib.so` — the suffixed file is a plain copy of the link output, so it can
      never be newer in isolation; simplest option.
  - Note: `cloneTalibNative` declares `outputs.dir` on the clone itself (line 16) and the build writes
    inside it, so it re-runs as a cheap no-op after this fix (its `doLast` skips the clone when `.git`
    exists) — acceptable.
  - Verify: run the build twice in a row; the second run should report `buildTalibNative UP-TO-DATE`
    **and** `buildTalibJna UP-TO-DATE` (no `mvn` invocation).

- [ ] **3. Decide how `aws-production` gets its sizing — workspace vs explicit var.**
  - Where: `infra/terraform/environments/aws-production/main.tf:16-21`
  - Problem: `local.environment = terraform.workspace == "prod" ? "prod" : "stage"`, but neither
    `atlantis.yaml` (no `workspace:` on `production-aws`) nor any CI step selects a `prod` workspace —
    the deploy workflows only run `init -backend=false` + `validate` (`forecast-deploy.yml:151-180`),
    none contains `terraform workspace`, and `aws-production/variables.tf` has no `environment` var.
    So the automated (Atlantis / ADR-026) apply always lands in the `default` workspace → **stage**
    sizing (t4g.large / 25 GB / $30 budget).
  - Additional gaps found during verification:
    - The `prod` workspace likely **does not exist yet** — nothing creates it, so the manual escape
      hatch is `terraform workspace new prod`, not just `select`.
    - Workspaces on the S3 backend are **separate state files** (`prod/terraform.tfstate` vs the
      default key), so a `prod` workspace is a second, independent set of resources — that may or may
      not be the intent.
  - Fix (pick one and document it in an ADR per project rules):
    - **(recommended)** replace the workspace keying with an explicit `var.environment`
      (`stage`/`prod`, same validation the hosting module already has), passed via tfvars or Atlantis
      `extra_plan_arguments: [-var, "environment=prod"]` — deterministic and visible in plan output; or
    - keep workspaces and wire them into `atlantis.yaml` (a `prod` project/workspace with
      `select || new` in the workflow, plus a second project for stage with different apply
      requirements); or
    - status quo + document the manual `workspace new/select prod` flow in the runbook (weakest —
      the prod tier is effectively unreachable by automation).
  - Verify: `terraform validate` in both envs; confirm the automated path produces the intended
    instance type for the intended tier.

## P2 — leftovers / contract drift

- [ ] **4. Delete the leftover `environments/aws/` directory.**
  - Where: `infra/terraform/environments/aws/main.tf` (tracked, **0 bytes**)
  - Problem: ADR-039:23 says the split "replaces `environments/aws/`", but the commit truncated
    `main.tf` to empty and left it tracked. It's dead cruft and makes `docs/PROJECT_STATUS.md`
    ("Terraform: 5 environments (`aws`, …)") inaccurate.
  - Fix: `git rm -r infra/terraform/environments/aws/`; update `PROJECT_STATUS.md` to list the 4 real
    environments (`aws-forecast`, `aws-production`, `gcp`, `azure`).
  - Note: other `environments/aws/` hits are historical (plan_v6, docs/reviews,
    atlantis-verification-plan) — leave those, they are dated documents.
  - Verify: no references to `environments/aws/` remain in live config
    (`grep -rn "environments/aws/" .github infra atlantis.yaml`).

- [ ] **5. Restore the conditional (or require) the hosting `ssm_read` attachment.**
  - Where: `infra/terraform/modules/hosting/main.tf:106-109`; `hosting/variables.tf:112-116`
  - Problem: the commit removed `count = var.ssm_read_policy_arn != "" ? 1 : 0` (confirmed in the
    diff), but the variable default is still `""`. Any caller that omits the var now gets an
    attachment with an empty `policy_arn` (invalid).
  - Verified "works today": aws-production passes `module.secrets.ssm_read_policy_arn`, and the
    `secrets` module unconditionally creates `aws_iam_policy.ssm_read` (secrets/main.tf:123) — a real
    ARN, so no live breakage.
  - Fix:
    - **(recommended)** re-add `count = var.ssm_read_policy_arn != "" ? 1 : 0` — preserves the
      optional-contract semantics that were there before; or
    - make `ssm_read_policy_arn` a required (non-optional) variable — stricter, but removes a
      legitimate "no SSM read" option.
  - Verify: `terraform validate` in `aws-production`; confirm the module still applies when the ARN is
    intentionally empty.

- [ ] **6. Align the `TalibAdapter` gate with the loader's resource resolution.**
  - Where: `computation/src/main/java/com/tickonomics/computation/talib/TalibAdapter.java:20`
  - Problem: `@ConditionalOnResource(resources = "libta-lib.so")` only checks the **generic** `.so`,
    while `TalibNativeLoader` prefers the arch-specific one. A jar shipping only the arch-specific
    `.so` would disable the bean even though the lib is loadable.
  - ⚠️ **Original suggestion corrected:** `resources = {"libta-lib.so", "libta-lib-x86_64.so",
    "libta-lib-aarch64.so"}` would be **wrong** — Spring Boot's `OnResourceCondition` requires
    **all** listed resources to be present (AND semantics), so the bean would be disabled in every
    build (no classpath contains all three). Do not use the multi-resource array.
  - Fix (pick one):
    - **(recommended, zero-risk)** leave the gate on the generic `.so`. The current behavior is
      fail-*safe*: a jar with only the arch-specific `.so` disables the bean (no TA-Lib strategies)
      rather than crashing — the reverse direction (bean enabled, lib unloadable) cannot happen
      because `resolveLibraryResource()` falls back to the generic resource. Just document the intent.
    - If arch-only jars must enable the bean: add a small `Conditional` (e.g.
      `TalibNativeCondition`) that reuses `resolveLibraryResource()` + a `getResourceAsStream`
      null-check (OR semantics aligned with the loader), and annotate with `@Conditional(...)`
      instead.
  - Verify: `./gradlew :app:bootRun` (or a context test) starts with the bean present/absent exactly
    as the chosen contract says, with only an arch-specific `.so` on the classpath.

## P3 — minor

- [ ] **7. Fall back to the generic lib on unsupported arches instead of throwing.**
  - Where: `computation/src/main/java/com/tickonomics/computation/talib/TalibNativeLoader.java:73-80`
  - Problem: `detectArchSuffix()` throws on e.g. `s390x`/`ppc64le` **before** the host-built generic
    `.so` (which would work) is tried — `resolveLibraryResource()` calls it first (line 63).
  - Fix: return a nullable suffix for unknown arches and let `resolveLibraryResource()` fall through
    to the generic resource; keep the terminal `IllegalStateException` in `ensureLoaded()` if neither
    is present.
  - ⚠️ Test coupling: `TalibNativeLoaderSpec.groovy:73-86`
    ("detectArchSuffix throws for unsupported architecture") pins the current throw — **update that
    spec in the same change**, or the suite goes red.
  - Note: low impact — only x86_64/aarch64 are realistically targeted; cheap to bundle with item 1.

- [ ] **8. Rewrite the commit message.**
  - Where: commit `271eb007` (message is `s`)
  - Safe now: the commit is **unpushed** — `origin/develop` is at `f8de1a18`, one commit behind — so
    `git commit --amend` needs no force-push. **Do this before pushing.**
  - Fix: amend to a real summary per the project's conventional-commit style, e.g.
    "feat(infra): split AWS Terraform into forecast/production stacks; bootstrap state backend;
    multi-arch TA-Lib native loader".

- [ ] **9. Correct ADR-039's "modules unchanged" claim.**
  - Where: `docs/adr/ADR-039-aws-terraform-forecast-production-split.md:44` (Decision D5)
  - Problem: D5 ("**Modules unchanged.**") contradicts the commit's own stat: `backup` (+34),
    `budget`, `hosting`, `orchestrator`, `storage` (+31) modified, `admin-role` added.
  - Fix: reword D5 to list the module edits that landed with the split (S3 SecureTransport policies +
    SSE in `storage`, lambda invoke-config rename + EBS encryption in `backup`, arm64/EBS-encryption/
    ssm-read changes in `hosting`, `stage`/`prod` rename in `budget`, new `admin-role` module).
    Pairs naturally with item 5.

---

## Confirmed sound (no action) — spot-checked 2026-09-06

- `aws-production` → `aws-forecast` `terraform_remote_state` references all resolve to real outputs
  (`vpc_id`, `public_subnet_id`, `results_bucket_name`, `s3_write_policy_arn`, and the three ECR
  URLs — all present in `aws-forecast/outputs.tf:1-63`).
- All module output names referenced by both environments exist (`database.ebs_volume_id`,
  `secrets.ssm_read_policy_arn`/`kms_key_arn` [secrets/main.tf:118-125], `observability.ebs_volume_id`,
  `dns-tls.zone_id`/`name_servers`, `hosting.*`, `backup.*`, `budget.budget_id`).
- `bootstrap` stack matches the `backend.tf` contract (versioning, SSE-AES256, public-access block,
  non-SSL deny, `PAY_PER_REQUEST` lock table) and the ownership boundary / import-recovery path is sound.
- Loader/`@ConditionalOnResource` resource paths are consistent with where `copyTalibNative` places
  the `.so` files (classpath root).
- `aws_lambda_function_event_invoke_config` (backup module, backup/main.tf:172) is the correct
  provider-v5 resource name.
- Runbook §7.7/§7.8 and the workspace documentation are thorough and accurate.

---

## Suggested landing order

1. **Item 1 + item 7 together** (same spec/loader family; unblocks the red arm64 job), then
   **item 2** (same build file; verify with a double-build UP-TO-DATE check).
2. **Item 8** — before pushing, while the amend is still force-push-free.
3. **Items 4 + 5 + 9** — one small infra/docs cleanup commit.
4. **Item 3** last — a design decision (var vs workspaces) that wants an ADR + Atlantis config
   change, not a reflex fix.
