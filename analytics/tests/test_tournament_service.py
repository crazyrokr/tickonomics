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
