"""Unit tests for the same-runner benchmark A/B comparator (ADR-030).

All tests follow Given-When-Then and exercise :mod:`compare_benchmarks` directly,
plus :func:`compare_benchmarks.main` for the CLI exit codes. The focus on edge
cases and false-positive scenarios (threshold boundary, zero baseline, missing
values, unmatched names, different runners) guards the gate against re-introducing
the cross-runner false positive it was built to eliminate.
"""

from __future__ import annotations

import json

import pytest

import compare_benchmarks as cb


def _run(
    *entries: tuple,
    cpu: str = "AMD EPYC 9V74 80-Core Processor",
) -> dict:
    """Build a minimal pytest-benchmark result dict.

    Each entry is (name, mean). The full stats block is populated so every metric
    path resolves; median/min/max mirror mean unless overridden via _stats().
    """
    benchmarks = []
    for entry in entries:
        name, mean = entry[0], entry[1]
        stats = entry[2] if len(entry) > 2 else {}
        defaults = {
            "mean": mean,
            "median": mean,
            "min": mean,
            "max": mean,
            "stddev": 0.0,
            "rounds": 100,
        }
        defaults.update(stats)
        benchmarks.append({"name": name, "stats": defaults})
    return {
        "machine_info": {"cpu": {"brand_raw": cpu}},
        "commit_info": {},
        "benchmarks": benchmarks,
    }


def _write(tmp_path, data: dict, filename: str):
    path = tmp_path / filename
    path.write_text(json.dumps(data), encoding="utf-8")
    return str(path)


# --- evaluate(): regression logic -----------------------------------------


def test_identical_runs_report_no_regression():
    """Given identical base and head, when evaluated, then no regression."""
    base = _run(("alpha", 100.0))
    head = _run(("alpha", 100.0))

    report = cb.evaluate(base, head)

    assert report.regressions == []
    assert report.compared[0].delta_percent == pytest.approx(0.0)


def test_head_slower_beyond_threshold_is_regression():
    """Given head 20% slower, when evaluated at 10%, then regression flagged."""
    base = _run(("alpha", 100.0))
    head = _run(("alpha", 120.0))

    report = cb.evaluate(base, head, threshold_percent=10.0)

    assert len(report.regressions) == 1
    assert report.compared[0].delta_percent == pytest.approx(20.0)


def test_head_slower_under_threshold_is_not_regression():
    """Given head 9% slower, when evaluated at 10%, then no regression."""
    base = _run(("alpha", 100.0))
    head = _run(("alpha", 109.0))

    report = cb.evaluate(base, head, threshold_percent=10.0)

    assert report.regressions == []
    assert report.compared[0].delta_percent == pytest.approx(9.0)


def test_delta_exactly_at_threshold_is_not_regression():
    """Given delta exactly at the threshold, when evaluated, then no regression.

    The gate uses strict ``>`` to match pytest-benchmark's PercentageRegressionCheck
    (the original failure log: ``77.18 > 10.00``). A delta equal to the threshold
    must not fire.
    """
    base = _run(("alpha", 100.0))
    head = _run(("alpha", 110.0))

    report = cb.evaluate(base, head, threshold_percent=10.0)

    assert report.regressions == []


def test_head_faster_is_improvement_not_regression():
    """Given head is faster, when evaluated, then negative delta and no regression."""
    base = _run(("alpha", 200.0))
    head = _run(("alpha", 100.0))

    report = cb.evaluate(base, head, threshold_percent=10.0)

    assert report.regressions == []
    assert report.compared[0].delta_percent == pytest.approx(-50.0)


def test_zero_baseline_yields_no_delta_and_no_regression():
    """Given a zero baseline mean, when evaluated, then no division error and no regression."""
    base = _run(("alpha", 0.0))
    head = _run(("alpha", 5.0))

    report = cb.evaluate(base, head, threshold_percent=10.0)

    assert report.regressions == []
    assert report.compared[0].delta_percent is None


def test_benchmark_only_in_head_is_reported_not_failure():
    """Given a benchmark present only in head, when evaluated, then listed, no regression."""
    base = _run(("alpha", 100.0))
    head = _run(("alpha", 100.0), ("beta", 100.0))

    report = cb.evaluate(base, head)

    assert report.regressions == []
    assert report.only_head == ["beta"]
    assert report.only_base == []


def test_benchmark_only_in_base_is_reported_not_failure():
    """Given a benchmark present only in base, when evaluated, then listed, no regression."""
    base = _run(("alpha", 100.0), ("beta", 100.0))
    head = _run(("alpha", 100.0))

    report = cb.evaluate(base, head)

    assert report.regressions == []
    assert report.only_base == ["beta"]
    assert report.only_head == []


def test_missing_stats_treated_as_unavailable():
    """Given a benchmark entry without stats, when evaluated, then n/a and no crash."""
    base = {
        "machine_info": {"cpu": {"brand_raw": "x"}},
        "commit_info": {},
        "benchmarks": [{"name": "alpha"}],
    }
    head = _run(("alpha", 100.0))

    report = cb.evaluate(base, head)

    assert report.regressions == []
    assert report.compared[0].base is None
    assert report.compared[0].delta_percent is None


def test_median_metric_compared_when_requested():
    """Given median differs but mean matches, when evaluated on median, then regression."""
    base = _run(("alpha", 100.0, {"median": 100.0}))
    head = _run(("alpha", 100.0, {"median": 130.0}))

    report = cb.evaluate(base, head, metric="median", threshold_percent=10.0)

    assert len(report.regressions) == 1
    assert report.metric == "median"


def test_custom_threshold_changes_verdict():
    """Given a 30% slowdown, when evaluated at 50%, then no regression."""
    base = _run(("alpha", 100.0))
    head = _run(("alpha", 130.0))

    report = cb.evaluate(base, head, threshold_percent=50.0)

    assert report.regressions == []


def test_machine_info_extracted_and_same_runner_flag():
    """Given identical CPUs in both files, when evaluated, then same_runner is True."""
    base = _run(("alpha", 100.0), cpu="AMD EPYC 9V74")
    head = _run(("alpha", 100.0), cpu="AMD EPYC 9V74")

    report = cb.evaluate(base, head)

    assert report.base_cpu == "AMD EPYC 9V74"
    assert report.head_cpu == "AMD EPYC 9V74"
    assert report.same_runner is True


def test_different_runners_flagged_as_not_same():
    """Given differing CPUs, when evaluated, then same_runner is False.

    This is the audit signal that exposes the original false-positive cause
    (Genoa baseline vs Milan run); it must never read as True across generations.
    """
    base = _run(("alpha", 100.0), cpu="AMD EPYC 9V74")
    head = _run(("alpha", 100.0), cpu="AMD EPYC 7763")

    report = cb.evaluate(base, head)

    assert report.same_runner is False


# --- formatting ------------------------------------------------------------


def test_markdown_includes_verdict_and_runner_line():
    """Given a regression report, when formatted, then verdict and runner line present."""
    report = cb.evaluate(
        _run(("alpha", 100.0), cpu="AMD EPYC 9V74"),
        _run(("alpha", 121.0), cpu="AMD EPYC 9V74"),
        threshold_percent=10.0,
    )

    text = cb.format_markdown(report)

    assert "REGRESSION" in text
    assert "AMD EPYC 9V74" in text
    assert "same runner = True" in text
    assert "| alpha |" in text


def test_text_format_reports_no_regression():
    """Given a clean report, when text-formatted, then NO REGRESSION line present."""
    report = cb.evaluate(_run(("alpha", 100.0)), _run(("alpha", 100.0)))

    assert "NO REGRESSION" in cb.format_text(report)


# --- main(): CLI exit codes -----------------------------------------------


def test_main_returns_ok_when_no_regression(tmp_path, capsys):
    """Given a clean A/B on disk, when run via main, then exit 0 and report printed."""
    base = _write(tmp_path, _run(("alpha", 100.0)), "base.json")
    head = _write(tmp_path, _run(("alpha", 100.0)), "head.json")

    code = cb.main([base, head, "--threshold", "10"])

    assert code == cb.EXIT_OK
    assert "NO REGRESSION" in capsys.readouterr().out


def test_main_returns_regression_when_beyond_threshold(tmp_path):
    """Given a >threshold slowdown on disk, when run via main, then exit 1."""
    base = _write(tmp_path, _run(("alpha", 100.0)), "base.json")
    head = _write(tmp_path, _run(("alpha", 121.0)), "head.json")

    code = cb.main([base, head])

    assert code == cb.EXIT_REGRESSION


def test_main_returns_usage_when_file_unreadable(tmp_path, capsys):
    """Given a missing baseline file, when run via main, then exit 2 and error to stderr."""
    head = _write(tmp_path, _run(("alpha", 100.0)), "head.json")

    code = cb.main([str(tmp_path / "missing.json"), head])

    assert code == cb.EXIT_USAGE
    assert "could not read" in capsys.readouterr().err


def test_main_returns_usage_on_invalid_json(tmp_path):
    """Given a malformed JSON file, when run via main, then exit 2."""
    bad = tmp_path / "bad.json"
    bad.write_text("{not valid json", encoding="utf-8")
    head = _write(tmp_path, _run(("alpha", 100.0)), "head.json")

    code = cb.main([str(bad), head])

    assert code == cb.EXIT_USAGE


def test_main_rejects_missing_args():
    """Given no file arguments, when run via main, then argparse exits non-zero."""
    with pytest.raises(SystemExit) as exc:
        cb.main([])

    assert exc.value.code != 0
