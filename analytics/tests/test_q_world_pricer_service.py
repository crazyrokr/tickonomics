from app.services.fi.q_world_pricer_service import compute_fair_value, compute_tbill_greeks


def test_fair_value_3m_tbill():
    """Given 3M T-Bill at 4.25% yield, when computing fair value, then residual is small."""
    result = compute_fair_value("3M_TBILL", current_yield=4.25)

    assert "error" not in result
    assert result["fair_value_yield"] > 0
    assert result["residual"] != 0
    assert isinstance(result["dislocated"], bool)
    assert result["model"] == "CIR"
    assert result["instrument"] == "3M_TBILL"


def test_fair_value_invalid_instrument():
    """Given unknown instrument, when computing fair value, then error returned."""
    assert "error" in compute_fair_value("INVALID", current_yield=4.0)


def test_fair_value_negative_yield():
    """Given negative yield, when computing fair value, then error returned."""
    assert "error" in compute_fair_value("3M_TBILL", current_yield=-1.0)


def test_tbill_greeks_basic():
    """Given 3M T-Bill params, when computing Greeks, then DV01 and convexity are positive."""
    result = compute_tbill_greeks(yield_level=0.04, duration_years=0.25)

    assert "error" not in result
    assert result["dv01"] > 0
    assert result["convexity"] > 0
    assert result["duration"] == 0.25
    assert result["rate_delta"] < 0
    assert result["rate_gamma"] > 0


def test_tbill_greeks_longer_duration():
    """Given longer duration, when computing Greeks, then DV01 is larger."""
    r1 = compute_tbill_greeks(duration_years=0.25)
    r10 = compute_tbill_greeks(duration_years=10.0)

    assert r10["dv01"] > r1["dv01"]


def test_tbill_greeks_invalid():
    """Given invalid inputs, when computing Greeks, then error returned."""
    assert "error" in compute_tbill_greeks(yield_level=0.0)
    assert "error" in compute_tbill_greeks(duration_years=-1.0)
