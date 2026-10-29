import numpy as np

from app.services.statistical.evt_risk_service import EvtRiskService


def test_fit_tail_distribution_with_fat_tails():
    """Given 500-point Student-t(df=3) series, when fit with quantile_u=0.95,
    then shape_xi > 0, tail_var_999 > threshold_u, status is SUCCESS."""
    # Given
    rng = np.random.default_rng(42)
    data = rng.standard_t(df=3, size=500).tolist()
    service = EvtRiskService()

    # When
    result = service.fit_tail_distribution(data, quantile_u=0.95)

    # Then
    assert result["status"] == "SUCCESS"
    assert result["shape_xi"] > 0
    assert result["tail_var_999"] > result["threshold_u"]


def test_insufficient_data_returns_error():
    """Given 50-point series, when fit, then status is ERROR."""
    # Given
    data = [float(i) for i in range(50)]
    service = EvtRiskService()

    # When
    result = service.fit_tail_distribution(data)

    # Then
    assert result["status"] == "ERROR"


def test_no_tail_returns_error():
    """Given uniform random series with quantile_u=0.99, when fit,
    then status is ERROR or FIT_FAILURE."""
    # Given
    rng = np.random.default_rng(7)
    data = rng.uniform(0, 1, size=200).tolist()
    service = EvtRiskService()

    # When
    result = service.fit_tail_distribution(data, quantile_u=0.99)

    # Then
    assert result["status"] in ("ERROR", "FIT_FAILURE")


def test_gumbel_fallback_when_xi_near_zero():
    """Given near-Gumbel tail data (xi ≈ 0), when fit, then tail_var is finite and
    the Gumbel-limit formula is used without division-by-zero."""
    # Given: exponential tail (GPD with xi=0 is the exponential/Gumbel domain)
    rng = np.random.default_rng(99)
    data = rng.exponential(scale=1.0, size=500).tolist()
    service = EvtRiskService()

    # When
    result = service.fit_tail_distribution(data, quantile_u=0.95)

    # Then
    assert result["status"] == "SUCCESS"
    assert np.isfinite(result["tail_var_999"])
    assert result["tail_var_999"] > result["threshold_u"]


def test_simulate_tail_paths_returns_correct_length():
    """Given shape and scale, when simulate 100 paths, then returns 100 values."""
    # Given
    service = EvtRiskService()
    shape_xi = 0.3
    scale_beta = 1.5

    # When
    paths = service.simulate_tail_paths(shape_xi, scale_beta, n_paths=100)

    # Then
    assert len(paths) == 100
    assert all(isinstance(v, float) for v in paths)
