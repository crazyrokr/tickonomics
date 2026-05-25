import numpy as np

from app.services.benchmark.tournament_service import evaluate_tournament


def test_tournament_basic():
    """Given 200 returns, when evaluating tournament, then all 3 models have results."""
    rng = np.random.default_rng(42)
    returns = (rng.standard_normal(200) * 0.01 + 0.0002).tolist()

    result = evaluate_tournament(returns, seed=42)

    assert "error" not in result
    assert "ili_rule_engine" in result["results"]
    assert "momentum" in result["results"]
    assert "lstm" in result["results"]
    for model_name, metrics in result["results"].items():
        assert "sharpe" in metrics
        assert "hit_rate" in metrics
    assert "regime_breakdown" in result
    assert "uptrend" in result["regime_breakdown"]


def test_tournament_insufficient_data():
    """Given fewer than 60 returns, when evaluating, then error returned."""
    assert "error" in evaluate_tournament([0.01] * 50)


def test_tournament_ranking_consistency():
    """Given positive-mean returns, when evaluating, then at least one model has positive Sharpe."""
    # Given
    rng = np.random.default_rng(42)
    returns = (rng.standard_normal(200) * 0.01 + 0.001).tolist()

    # When
    result = evaluate_tournament(returns, seed=42)

    # Then
    assert "error" not in result
    sharpes = [m["sharpe"] for m in result["results"].values()]
    assert any(s > 0 for s in sharpes)
