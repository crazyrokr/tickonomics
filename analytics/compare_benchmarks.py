"""Same-runner benchmark A/B comparator (ADR-030).

Reads two pytest-benchmark result JSON files — a baseline and a candidate, both
produced on the same runner in a single CI job — and decides whether the
candidate regressed beyond a threshold on a chosen statistic. This is the
authoritative regression gate for the same-runner A/B step in benchmark.yml; it
replaces pytest-benchmark's own ``--benchmark-compare``/``--benchmark-compare-fail``
gate, which compared a cached baseline across heterogeneous GitHub-hosted runners
and fired false positives whenever the runner CPU generation changed.

CLI::

    python compare_benchmarks.py BASE.json HEAD.json [--threshold 10] [--metric mean] [--format markdown]

Exit codes: 0 = no regression, 1 = regression beyond threshold, 2 = usage / IO
or parse error. The comparison logic lives in :func:`evaluate`, which is pure and
unit-tested directly; :func:`main` only handles argument parsing and IO.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field

EXIT_OK = 0
EXIT_REGRESSION = 1
EXIT_USAGE = 2

METRICS = ("mean", "median", "min", "max")
FORMATS = ("markdown", "text")


@dataclass(frozen=True)
class StatPoint:
    """A single benchmark's value for the chosen metric, or its absence."""

    name: str
    value: float | None


@dataclass
class Comparison:
    """Pairwise result for one benchmark present in both files."""

    name: str
    base: float | None
    head: float | None
    delta_percent: float | None
    regressed: bool


@dataclass
class Report:
    """Full result of comparing two benchmark runs."""

    metric: str
    threshold_percent: float
    base_cpu: str | None
    head_cpu: str | None
    compared: list[Comparison] = field(default_factory=list)
    only_base: list[str] = field(default_factory=list)
    only_head: list[str] = field(default_factory=list)
    base_path: str | None = None
    head_path: str | None = None

    @property
    def same_runner(self) -> bool | None:
        if self.base_cpu is None or self.head_cpu is None:
            return None
        return self.base_cpu == self.head_cpu

    @property
    def regressions(self) -> list[Comparison]:
        return [c for c in self.compared if c.regressed]


def load_results(path: str) -> dict:
    """Load and parse a pytest-benchmark result JSON file."""
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def cpu_brand(data: dict) -> str | None:
    """Extract the runner CPU brand string from a result file, if present."""
    return data.get("machine_info", {}).get("cpu", {}).get("brand_raw")


def _stat_value(entry: dict, metric: str) -> float | None:
    """Read a metric from one benchmark entry, tolerating missing stats."""
    stats = entry.get("stats")
    if not isinstance(stats, dict):
        value = entry.get(metric)
    else:
        value = stats.get(metric, entry.get(metric))
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def extract(data: dict, metric: str) -> dict[str, StatPoint]:
    """Map benchmark name -> :class:`StatPoint` for every benchmark in a run."""
    points: dict[str, StatPoint] = {}
    for entry in data.get("benchmarks", []):
        name = entry.get("name")
        if not name:
            continue
        points[name] = StatPoint(name=name, value=_stat_value(entry, metric))
    return points


def evaluate(
    base: dict,
    head: dict,
    metric: str = "mean",
    threshold_percent: float = 10.0,
) -> Report:
    """Compare two parsed benchmark runs and return a :class:`Report`.

    A benchmark regresses only when it appears in both runs, both values are
    present, the baseline is non-zero, and the candidate is slower by strictly
    more than ``threshold_percent`` (matching pytest-benchmark's ``>`` rule, so a
    delta exactly at the threshold is not a failure). Missing benchmarks, missing
    values, and a zero baseline are reported but never counted as regressions.
    """
    base_points = extract(base, metric)
    head_points = extract(head, metric)

    report = Report(
        metric=metric,
        threshold_percent=threshold_percent,
        base_cpu=cpu_brand(base),
        head_cpu=cpu_brand(head),
    )

    for name in sorted(base_points.keys() | head_points.keys()):
        in_base = name in base_points
        in_head = name in head_points
        if in_base and not in_head:
            report.only_base.append(name)
            continue
        if in_head and not in_base:
            report.only_head.append(name)
            continue

        base_value = base_points[name].value
        head_value = head_points[name].value
        delta_percent: float | None
        regressed = False
        if base_value is None or head_value is None or base_value == 0:
            delta_percent = None
        else:
            delta_percent = (head_value - base_value) / abs(base_value) * 100.0
            regressed = delta_percent > threshold_percent
        report.compared.append(
            Comparison(
                name=name,
                base=base_value,
                head=head_value,
                delta_percent=delta_percent,
                regressed=regressed,
            )
        )

    return report


def _fmt_seconds(seconds: float | None) -> str:
    if seconds is None:
        return "n/a"
    return f"{seconds * 1e6:,.3f}"


def _fmt_delta(delta_percent: float | None) -> str:
    if delta_percent is None:
        return "n/a"
    sign = "+" if delta_percent >= 0 else ""
    return f"{sign}{delta_percent:.2f}%"


def format_markdown(report: Report) -> str:
    """Render a report as a Markdown table plus a one-line verdict."""
    lines: list[str] = [
        f"| benchmark | base {report.metric} (us) | head {report.metric} (us) | delta | status |",
        "| --- | ---: | ---: | ---: | --- |",
    ]
    if report.compared:
        for item in report.compared:
            status = "REGRESSION" if item.regressed else "ok"
            if item.delta_percent is None:
                status = "n/a"
            lines.append(
                f"| {item.name} | {_fmt_seconds(item.base)} | "
                f"{_fmt_seconds(item.head)} | {_fmt_delta(item.delta_percent)} | {status} |"
            )
    else:
        lines.append("| _(no benchmarks matched by name)_ | | | | |")

    machine = (
        f"Runner: base = {report.base_cpu or 'unknown'}; "
        f"head = {report.head_cpu or 'unknown'}; "
        f"same runner = {report.same_runner}."
    )
    lines.append("")
    lines.append(machine)

    regression_count = len(report.regressions)
    compared_count = len(report.compared)
    verdict = (
        f"Result: REGRESSION ({regression_count} of {compared_count} benchmark(s) "
        f"exceeded +{report.threshold_percent:.2f}% on {report.metric})."
        if regression_count
        else f"Result: NO REGRESSION (0 of {compared_count} benchmark(s) exceeded "
        f"+{report.threshold_percent:.2f}% on {report.metric})."
    )
    lines.append(verdict)

    if report.only_base:
        lines.append(f"Only in base: {', '.join(report.only_base)}.")
    if report.only_head:
        lines.append(f"Only in head: {', '.join(report.only_head)}.")

    return "\n".join(lines)


def format_text(report: Report) -> str:
    """Render a report as plain text (one benchmark per line)."""
    lines: list[str] = []
    for item in report.compared:
        status = "REGRESSION" if item.regressed else "ok"
        if item.delta_percent is None:
            status = "n/a"
        lines.append(
            f"{item.name}: base={_fmt_seconds(item.base)}us "
            f"head={_fmt_seconds(item.head)}us delta={_fmt_delta(item.delta_percent)} [{status}]"
        )
    if not report.compared:
        lines.append("(no benchmarks matched by name)")
    regression_count = len(report.regressions)
    lines.append(
        f"Runner: base={report.base_cpu or 'unknown'} "
        f"head={report.head_cpu or 'unknown'} same={report.same_runner}"
    )
    lines.append(
        f"Result: {'REGRESSION' if regression_count else 'NO REGRESSION'} "
        f"({regression_count}/{len(report.compared)} > +{report.threshold_percent:.2f}% on {report.metric})"
    )
    return "\n".join(lines)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Compare two pytest-benchmark runs (same-runner A/B).",
    )
    parser.add_argument("base", help="path to the baseline result JSON")
    parser.add_argument("head", help="path to the candidate result JSON")
    parser.add_argument(
        "--threshold",
        type=float,
        default=10.0,
        help="regression threshold in percent (default: 10); delta must exceed it (>)",
    )
    parser.add_argument(
        "--metric",
        choices=METRICS,
        default="mean",
        help="statistic to compare (default: mean)",
    )
    parser.add_argument(
        "--format",
        choices=FORMATS,
        default="markdown",
        help="output format (default: markdown)",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)

    try:
        base = load_results(args.base)
        head = load_results(args.head)
    except (OSError, json.JSONDecodeError) as error:
        print(f"error: could not read benchmark JSON: {error}", file=sys.stderr)
        return EXIT_USAGE

    report = evaluate(
        base,
        head,
        metric=args.metric,
        threshold_percent=args.threshold,
    )
    report.base_path = args.base
    report.head_path = args.head

    formatter = format_markdown if args.format == "markdown" else format_text
    print(formatter(report))

    return EXIT_REGRESSION if report.regressions else EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
