from app.services.optimizer.sgd_optimizer_service import compute_weight_delta


def test_weight_delta_basic_update():
    """Given weights and performance data, when computing delta, then updated weights still sum to 1."""
    weights = {"rrp": 0.4, "spread": 0.35, "vol": 0.25}
    performance = [
        {"rrp": 0.85, "spread": 0.72, "vol": 0.60},
        {"rrp": 0.90, "spread": 0.70, "vol": 0.55},
        {"rrp": 0.88, "spread": 0.75, "vol": 0.58},
    ]

    result = compute_weight_delta(weights, performance)

    assert "error" not in result
    assert "updated_weights" in result
    assert "weight_deltas" in result
    total = sum(result["updated_weights"].values())
    assert abs(total - 1.0) < 0.01
    assert result["learning_rate"] == 0.01


def test_weight_delta_increases_best_performer():
    """Given rrp outperforms others, when computing delta, then rrp weight increases."""
    weights = {"rrp": 0.4, "spread": 0.35, "vol": 0.25}
    performance = [
        {"rrp": 0.95, "spread": 0.50, "vol": 0.40},
        {"rrp": 0.90, "spread": 0.45, "vol": 0.35},
    ]

    result = compute_weight_delta(weights, performance)

    assert "error" not in result
    assert result["updated_weights"]["rrp"] > 0.4


def test_weight_delta_respects_max_delta():
    """Given max_delta=0.01, when computing delta, then no delta exceeds 0.01 in absolute value."""
    weights = {"a": 0.5, "b": 0.5}
    performance = [
        {"a": 1.0, "b": 0.0},
        {"a": 1.0, "b": 0.0},
    ]

    result = compute_weight_delta(weights, performance, max_delta=0.01)

    for delta in result["weight_deltas"].values():
        assert abs(delta) <= 0.01 + 1e-9


def test_weight_delta_lookback_limit():
    """Given lookback_days=1, when computing delta, then only last sample is used."""
    weights = {"x": 0.5, "y": 0.5}
    performance = [
        {"x": 0.1, "y": 0.9},
        {"x": 0.9, "y": 0.1},
        {"x": 0.1, "y": 0.9},
    ]

    result = compute_weight_delta(weights, performance, lookback_days=1)

    assert "error" not in result
    assert result["lookback_samples"] == 1


def test_weight_delta_empty_weights_error():
    """Given empty weights, when computing delta, then error returned."""
    result = compute_weight_delta({}, [{"a": 0.5}])

    assert "error" in result


def test_weight_delta_empty_performance_error():
    """Given empty performance, when computing delta, then error returned."""
    result = compute_weight_delta({"a": 1.0}, [])

    assert "error" in result


def test_weight_delta_weights_not_summing_to_one_error():
    """Given weights that do not sum to 1, when computing delta, then error returned."""
    result = compute_weight_delta({"a": 0.5, "b": 0.3}, [{"a": 0.5, "b": 0.5}])

    assert "error" in result


def test_weight_delta_zero_performance():
    """Given all-zero performance, when computing delta, then all deltas are zero."""
    weights = {"a": 0.5, "b": 0.5}
    performance = [
        {"a": 0.0, "b": 0.0},
    ]

    result = compute_weight_delta(weights, performance)

    assert "error" not in result
    for delta in result["weight_deltas"].values():
        assert delta == 0.0
