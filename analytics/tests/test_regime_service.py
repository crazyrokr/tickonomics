import numpy as np
import pytest

from app.services.regime.regime_service import (
    cnn_lstm_regime,
    garch_regime,
    qed_regime,
    rahf_regime,
)


def _make_returns(n: int = 500, seed: int = 42) -> list[float]:
    rng = np.random.default_rng(seed)
    return (rng.standard_normal(n) * 0.02).tolist()


def test_garch_regime_classifies_correctly():
    """Given 500 returns, when GARCH regime detection, then regime is one of the valid labels."""
    returns = _make_returns(500)

    result = garch_regime(returns)

    assert "error" not in result
    assert result["regime"] in ("LOW_VOL", "NORMAL", "ELEVATED", "HIGH_VOL")
    assert result["conditional_volatility"] > 0
    assert result["forecast_1d"] > 0
    assert 0 <= result["percentile"] <= 100
    assert "boundaries" in result
    assert "low_vol_threshold" in result["boundaries"]


def test_garch_regime_high_volatility():
    """Given highly volatile returns, when GARCH regime, then regime is HIGH_VOL or ELEVATED."""
    rng = np.random.default_rng(99)
    returns = (rng.standard_normal(300) * 0.10).tolist()

    result = garch_regime(returns)

    assert "error" not in result
    assert result["regime"] in ("HIGH_VOL", "ELEVATED", "NORMAL")


def test_garch_regime_insufficient_data():
    """Given fewer than 100 returns, when GARCH regime, then error returned."""
    result = garch_regime([0.01] * 50)

    assert "error" in result


def test_cnn_lstm_regime_returns_valid_output():
    """Given sufficient returns, when CNN-LSTM regime, then regime is a valid label with confidence."""
    returns = _make_returns(300)

    result = cnn_lstm_regime(returns, lookback=30)

    assert "error" not in result
    assert result["regime"] in ("LOW_VOL", "NORMAL", "HIGH_VOL")
    assert 0 <= result["confidence"] <= 1
    assert "transition_probability" in result
    probs = result["transition_probability"]
    assert abs(sum(probs.values()) - 1.0) < 0.01
    assert result["lookback"] == 30


def test_cnn_lstm_regime_insufficient_data():
    """Given fewer returns than 2*lookback, when CNN-LSTM regime, then error returned."""
    result = cnn_lstm_regime([0.01] * 50, lookback=60)

    assert "error" in result


def test_qed_regime_stable_state():
    """Given low-volatility capital flows, when QED regime, then regime is STABLE."""
    proxies = {
        "repo_flows": [0.1, 0.1, 0.1, 0.1, 0.1],
        "tbill_demand": [0.5, 0.5, 0.5, 0.5, 0.5],
    }
    state = {"potential_value": 0.1, "velocity": 0.01}

    result = qed_regime(proxies, state)

    assert "error" not in result
    assert result["regime"] in ("STABLE", "METASTABLE", "UNSTABLE")
    assert 0 <= result["crash_probability"] <= 1
    assert result["barrier_distance"] >= 0
    assert "potential_parameters" in result


def test_qed_regime_no_proxies():
    """Given empty proxies, when QED regime, then error returned."""
    result = qed_regime({}, {"potential_value": 0.5, "velocity": 0.0})

    assert "error" in result


def test_qed_regime_short_series():
    """Given proxy series shorter than 3 points, when QED regime, then error returned."""
    proxies = {"repo_flows": [0.1, 0.2]}
    result = qed_regime(proxies, {"potential_value": 0.5, "velocity": 0.0})

    assert "error" in result


def test_rahf_regime_returns_valid_output():
    """Given 200 returns, when RAHF regime, then regime is valid with both components."""
    returns = _make_returns(300)

    result = rahf_regime(returns, n_harmonics=3)

    assert "error" not in result
    assert result["regime"] in ("LOW_VOL", "NORMAL", "HIGH_VOL")
    assert "garch_component" in result
    assert "nn_component" in result
    assert "regime" in result["garch_component"]
    assert "regime" in result["nn_component"]
    assert result["msfe"] >= 0
    assert result["dominant_cycle_days"] > 0


def test_rahf_regime_insufficient_data():
    """Given fewer than 100 returns, when RAHF regime, then error returned."""
    result = rahf_regime([0.01] * 50)

    assert "error" in result


def test_cnn_lstm_regime_output_shape():
    """Given 300 returns, when CNN-LSTM regime, then output has valid confidence."""
    # Given
    returns = _make_returns(300)

    # When
    result = cnn_lstm_regime(returns, lookback=30)

    # Then
    assert "error" not in result
    assert 0 <= result["confidence"] <= 1.0


def test_garch_regime_two_clusters():
    """Given bimodal returns (low + high vol), when GARCH regime, then detects a valid regime."""
    # Given
    rng = np.random.default_rng(42)
    calm = rng.standard_normal(200) * 0.01
    turbulent = rng.standard_normal(200) * 0.05
    returns = np.concatenate([calm, turbulent]).tolist()

    # When
    result = garch_regime(returns)

    # Then
    assert "error" not in result
    assert result["regime"] in ("LOW_VOL", "NORMAL", "ELEVATED", "HIGH_VOL")
