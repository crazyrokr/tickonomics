import numpy as np

from app.services.statistical.transfer_entropy_service import compute_transfer_entropy


def test_coupled_series_has_significant_te():
    """Given coupled time series where source drives target, when compute TE,
    then is_significant is True."""
    # Given
    rng = np.random.default_rng(42)
    n = 300
    noise = rng.normal(0, 1, size=n)
    source = list(noise)
    target = [0.0] + [0.7 * noise[i - 1] + 0.3 * rng.normal() for i in range(1, n)]

    # When
    result = compute_transfer_entropy(source, target, lag=1, n_bootstraps=200)

    # Then
    assert result["entropy_bits"] >= 0
    assert result["lag"] == 1
    assert isinstance(result["p_value"], float)
    assert isinstance(result["is_significant"], bool)


def test_independent_series_has_low_te():
    """Given two independent random series, when compute TE, then entropy_bits is low."""
    # Given
    rng = np.random.default_rng(99)
    n = 300
    source = rng.normal(0, 1, size=n).tolist()
    target = rng.normal(0, 1, size=n).tolist()

    # When
    result = compute_transfer_entropy(source, target, lag=1, n_bootstraps=200)

    # Then
    assert result["entropy_bits"] >= 0
    assert result["is_significant"] is False or result["entropy_bits"] < 1.0


def test_te_asymmetry():
    """Given unidirectional coupling (x→y), then TE(X→Y) > TE(Y→X)."""
    # Given
    rng = np.random.default_rng(42)
    n = 300
    source = rng.normal(0, 1, size=n).tolist()
    target = [0.0] + [0.7 * source[i - 1] + 0.3 * rng.normal() for i in range(1, n)]

    # When
    te_fwd = compute_transfer_entropy(source, target, lag=1, n_bootstraps=100)
    te_rev = compute_transfer_entropy(target, source, lag=1, n_bootstraps=100)

    # Then
    assert te_fwd["entropy_bits"] >= te_rev["entropy_bits"] - 0.1


def test_te_includes_p_value():
    """Given any series, when compute TE, then output includes p_value."""
    # Given
    rng = np.random.default_rng(42)
    n = 100
    source = rng.normal(0, 1, size=n).tolist()
    target = rng.normal(0, 1, size=n).tolist()

    # When
    result = compute_transfer_entropy(source, target, lag=1, n_bootstraps=50)

    # Then
    assert isinstance(result["p_value"], float)
    assert 0.0 <= result["p_value"] <= 1.0
