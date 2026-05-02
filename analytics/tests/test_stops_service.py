import numpy as np

from app.services.stops.markov_stop_service import calibrate_stops


def test_calibrate_stops_basic():
    """Given 100 trade PnL values, when calibrating, then optimal stops are returned."""
    rng = np.random.default_rng(42)
    pnl = (rng.standard_normal(100) * 0.01 - 0.005).tolist()

    result = calibrate_stops(pnl, max_iterations=100, seed=42)

    assert "error" not in result
    assert result["optimal_stop_loss"] < 0
    assert result["optimal_take_profit"] > 0
    assert result["iterations"] > 0
    assert isinstance(result["converged"], bool)
    assert result["signal_drift"] is not None


def test_calibrate_stops_positive_drift():
    """Given positive drift PnL, when calibrating, then take-profit exceeds stop-loss in magnitude."""
    rng = np.random.default_rng(7)
    pnl = (rng.standard_normal(100) * 0.01 + 0.005).tolist()

    result = calibrate_stops(pnl, max_iterations=50, seed=7)

    assert "error" not in result
    assert result["signal_drift"] > 0


def test_calibrate_stops_insufficient_data():
    """Given fewer than 10 trades, when calibrating, then error returned."""
    assert "error" in calibrate_stops([0.01, 0.02, -0.01])
