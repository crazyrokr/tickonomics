#!/usr/bin/env python3
"""Structural contract test for the Track 9 CI/CD pipeline.

Parses every GitHub Actions workflow under .github/workflows/ and asserts the
post-finalization contract described in ADR-016. Exits non-zero with a clear
message on the first breach. This is the regression test for the workflow
configuration: it is intentionally written before the workflow changes so it
runs red, then green once the changes land.

Each assertion follows Given-When-Then and reports a human-readable failure.
"""

from __future__ import annotations

import sys
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
WORKFLOWS = REPO_ROOT / ".github" / "workflows"
LIGHTHOUSE_RC = REPO_ROOT / ".github" / "lighthouse" / "lighthouserc.js"

FAILURES: list[str] = []


def fail(message: str) -> None:
    FAILURES.append(message)


def load(name: str) -> dict:
    """GIVEN a workflow file WHEN parsed THEN return its dict (or record failure)."""
    path = WORKFLOWS / name
    if not path.exists():
        fail(f"MISSING workflow: {name}")
        return {}
    with path.open() as handle:
        return yaml.safe_load(handle) or {}


def triggers(data: dict) -> dict:
    """Return the `on:` trigger block, tolerant of PyYAML mapping `on` to bool True."""
    if "on" in data:
        return data["on"]
    if True in data:
        return data[True]
    return {}


def has_push_branches(data: dict) -> list[str]:
    on = triggers(data)
    push = on.get("push") if isinstance(on, dict) else None
    if isinstance(push, dict):
        return push.get("branches") or []
    return []


def workflow_dispatch_present(data: dict) -> bool:
    on = triggers(data)
    return isinstance(on, dict) and "workflow_dispatch" in on


def jobs_of(data: dict) -> dict:
    return data.get("jobs") or {}


def step_text(steps: list[dict], key: str = "run") -> str:
    """Collect every textual surface of a step list: run, uses, env values,
    and with.run/with.script. Used so contract assertions match across
    step-action (`uses:`), shell (`run:`/`with.script`), and env-bearing steps."""
    parts: list[str] = []

    def add(value) -> None:
        if isinstance(value, str):
            parts.append(value)
        elif isinstance(value, list):
            parts.extend(str(line) for line in value)

    for step in steps or []:
        add(step.get("run"))
        add(step.get("uses"))
        env = step.get("env")
        if isinstance(env, dict):
            add(list(env.values()))
        with_block = step.get("with")
        if isinstance(with_block, dict):
            add(with_block.get("run"))
            add(with_block.get("script"))
    return "\n".join(parts)


def steps_of(job: dict) -> list[dict]:
    return job.get("steps") or []


def assert_chaos_tests_uses_finnhub(raw: str) -> None:
    """GIVEN chaos-tests.yml WHEN scenario 4 read THEN it names Finnhub, not Polygon."""
    if "Polygon" in raw:
        fail("chaos-tests.yml still references deprecated 'Polygon' source")
    if "Finnhub" not in raw:
        fail("chaos-tests.yml scenario 4 does not reference 'Finnhub'")


def assert_chaos_tests_posts_report(data: dict) -> None:
    """GIVEN chaos-tests.yml WHEN parsed THEN issues:write perm + report-comment step exist."""
    perms = data.get("permissions")
    issues_write = False
    if isinstance(perms, dict):
        issues_write = perms.get("issues") == "write"
    elif perms == "write-all":
        issues_write = True
    if not issues_write:
        fail("chaos-tests.yml lacks 'permissions: issues: write'")

    blob = step_text([s for job in jobs_of(data).values() for s in steps_of(job)])
    if "gh issue comment" not in blob:
        fail("chaos-tests.yml does not post a chaos report via 'gh issue comment'")


def assert_deploy_staging_contract(data: dict) -> None:
    """GIVEN deploy-staging.yml WHEN parsed THEN push to main + GHCR push + Flyway."""
    branches = has_push_branches(data)
    if branches != ["main"]:
        fail(f"deploy-staging.yml push branches expected ['main'], got {branches}")

    jobs = jobs_of(data)
    if "build-and-push" not in jobs:
        fail("deploy-staging.yml missing 'build-and-push' job")
    build_steps = step_text(steps_of(jobs.get("build-and-push", {})))
    if "docker/build-push-action" not in build_steps and "buildx build" not in build_steps:
        fail("deploy-staging.yml build-and-push does not push images")

    all_runs = "\n".join(
        step_text(steps_of(job)) for job in jobs.values()
    )
    if "flyway" not in all_runs.lower():
        fail("deploy-staging.yml has no Flyway migration step")


def assert_deploy_production_contract(data: dict) -> None:
    """GIVEN deploy-production.yml WHEN parsed THEN workflow_dispatch + production env, no push."""
    if not workflow_dispatch_present(data):
        fail("deploy-production.yml is not triggered by workflow_dispatch")
    if triggers(data).get("push"):
        fail("deploy-production.yml should not trigger on push (manual promotion only)")

    environments = [
        job.get("environment") for job in jobs_of(data).values()
    ]
    if "production" not in environments:
        fail("deploy-production.yml has no job with environment: production")


def assert_pr_checks_contract(data: dict) -> None:
    """GIVEN pr-checks.yml WHEN parsed THEN code-quality job + OTel agent step present."""
    jobs = jobs_of(data)
    if "code-quality" not in jobs:
        fail("pr-checks.yml missing advisory 'code-quality' lint job")

    java_steps = "\n".join(
        step_text(steps_of(job)) for job in jobs.values()
    )
    if "opentelemetry-javaagent" not in java_steps:
        fail("pr-checks.yml java job does not attach the OpenTelemetry agent")
    if "-javaagent:" not in java_steps:
        fail("pr-checks.yml java job does not attach the OTel agent via -javaagent:")


def assert_pr_checks_runs_on_all_pull_requests(data: dict) -> None:
    """GIVEN pr-checks.yml WHEN parsed THEN its pull_request trigger has no branch
    filter, so the Java build & test job runs on every PR regardless of target
    branch (ADR-029). A `types:` activity filter is allowed; `branches` /
    `branches-ignore` are not."""
    on = triggers(data)
    if not isinstance(on, dict) or "pull_request" not in on:
        fail("pr-checks.yml is not triggered by pull_request")
        return
    pr = on.get("pull_request")
    if isinstance(pr, dict) and ("branches" in pr or "branches-ignore" in pr):
        fail("pr-checks.yml restricts pull_request by branches; per ADR-029 it "
             "must run on all pull_request events")


def assert_benchmark_workflow(data: dict) -> None:
    """GIVEN benchmark.yml WHEN parsed THEN benchmark job runs a same-runner A/B
    (PR base vs head checked out in one job) gated by compare_benchmarks.py.

    This supersedes the cached-baseline + --benchmark-compare design of ADR-024,
    which compared across heterogeneous GitHub-hosted runners and fired false
    positives on runner CPU-generation changes. The new contract (ADR-030) keeps
    base and head on a single runner, so the comparison is machine-equivalent by
    construction.
    """
    if "benchmark" not in jobs_of(data):
        fail("benchmark.yml missing 'benchmark' job")
    blob = step_text([s for job in jobs_of(data).values() for s in steps_of(job)])
    if "pytest-benchmark" not in blob and "pytest" not in blob:
        fail("benchmark.yml does not run a pytest benchmark")
    if "compare_benchmarks" not in blob:
        fail("benchmark.yml does not gate via compare_benchmarks.py (same-runner A/B)")
    if "github.event.pull_request.base.sha" not in blob:
        fail("benchmark.yml does not check out the PR base for same-runner A/B (ADR-030)")


def assert_codeql_workflow(data: dict) -> None:
    """GIVEN codeql.yml WHEN parsed THEN analyze job + security-events write permission."""
    jobs = jobs_of(data)
    has_analyze = any(
        "analyze" in str(job.get("name", "")).lower()
        or "codeql" in str(job.get("name", "")).lower()
        or any("github/codeql-action/analyze" in str(s.get("uses", "")) for s in steps_of(job))
        for job in jobs.values()
    )
    if not has_analyze:
        fail("codeql.yml has no CodeQL analyze step")
    perms = data.get("permissions")
    job_perms = [
        j.get("permissions") for j in jobs.values()
        if isinstance(j.get("permissions"), dict)
    ]
    sec_write = (
        (isinstance(perms, dict) and perms.get("security-events") == "write")
        or perms == "write-all"
        or any(p.get("security-events") == "write" for p in job_perms)
    )
    if not sec_write:
        fail("codeql.yml lacks 'permissions: security-events: write'")


def assert_chaos_benchmark_real() -> None:
    """GIVEN chaos-benchmark.yml WHEN parsed THEN benchmark job is not an echo placeholder."""
    data = load("chaos-benchmark.yml")
    bench = jobs_of(data).get("benchmark", {})
    blob = step_text(steps_of(bench))
    if not blob.strip():
        fail("chaos-benchmark.yml 'benchmark' job has no steps")
    if "Benchmark placeholder" in blob and "pytest" not in blob:
        fail("chaos-benchmark.yml 'benchmark' job is still an echo placeholder")


def assert_lighthouserc_exists() -> None:
    """GIVEN the repo WHEN checked THEN .github/lighthouse/lighthouserc.js exists."""
    if not LIGHTHOUSE_RC.exists():
        fail(f"MISSING Lighthouse config: {LIGHTHOUSE_RC.relative_to(REPO_ROOT)}")


def assert_no_secrets_in_if(name: str, data: dict) -> None:
    """GIVEN a workflow WHEN its if-expressions scanned THEN none reference the
    secrets context, which GitHub Actions forbids in `if` (job or step level).
    Gate on `vars.*` instead."""
    def check_if(label: str, value) -> None:
        if isinstance(value, str) and "secrets." in value:
            fail(f"{name}: {label} 'if' references the forbidden secrets context: {value.strip()}")

    for job_name, job in jobs_of(data).items():
        check_if(f"job '{job_name}'", job.get("if"))
        for index, step in enumerate(steps_of(job), start=1):
            check_if(f"job '{job_name}' step #{index}", step.get("if"))


def main() -> int:
    chaos_raw = (WORKFLOWS / "chaos-tests.yml").read_text() if (WORKFLOWS / "chaos-tests.yml").exists() else ""
    assert_chaos_tests_uses_finnhub(chaos_raw)
    assert_chaos_tests_posts_report(load("chaos-tests.yml"))
    assert_deploy_staging_contract(load("deploy-staging.yml"))
    assert_deploy_production_contract(load("deploy-production.yml"))
    assert_pr_checks_contract(load("pr-checks.yml"))
    assert_pr_checks_runs_on_all_pull_requests(load("pr-checks.yml"))
    assert_benchmark_workflow(load("benchmark.yml"))
    assert_codeql_workflow(load("codeql.yml"))
    assert_chaos_benchmark_real()
    assert_lighthouserc_exists()
    for workflow in sorted(WORKFLOWS.glob("*.yml")):
        assert_no_secrets_in_if(workflow.name, load(workflow.name))

    if FAILURES:
        print("CI/CD contract FAILED:")
        for message in FAILURES:
            print(f"  - {message}")
        return 1

    print("CI/CD contract OK: all Track 9 finalization assertions passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
