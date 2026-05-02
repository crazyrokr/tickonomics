from app.services.greeks.bsm_service import aggregate_gex, bsm_greeks


def test_bsm_call_greeks_atm():
    """Given ATM call, when computing Greeks, then delta near 0.5 and gamma > 0."""
    result = bsm_greeks(
        spot_price=100.0,
        strike_price=100.0,
        time_to_expiry=1.0,
        risk_free_rate=0.05,
        implied_volatility=0.2,
        option_type="call",
    )

    assert "error" not in result
    assert 0.4 < result["delta"] < 0.7
    assert result["gamma"] > 0
    assert result["theta"] < 0
    assert result["vega"] > 0
    assert result["price"] > 0
    assert result["option_type"] == "call"


def test_bsm_put_greeks_atm():
    """Given ATM put, when computing Greeks, then delta near -0.5."""
    result = bsm_greeks(
        spot_price=100.0,
        strike_price=100.0,
        time_to_expiry=1.0,
        risk_free_rate=0.05,
        implied_volatility=0.2,
        option_type="put",
    )

    assert "error" not in result
    assert -0.7 < result["delta"] < -0.3
    assert result["gamma"] > 0
    assert result["price"] > 0


def test_bsm_call_put_parity():
    """Given same inputs, when call - put, then price difference equals S - K*exp(-rT)."""
    import math
    call = bsm_greeks(100.0, 100.0, 1.0, 0.05, 0.2, "call")
    put = bsm_greeks(100.0, 100.0, 1.0, 0.05, 0.2, "put")

    parity = call["price"] - put["price"]
    expected = 100.0 - 100.0 * math.exp(-0.05 * 1.0)
    assert abs(parity - expected) < 0.01


def test_bsm_deep_itm_call():
    """Given deep ITM call, when computing Greeks, then delta near 1.0."""
    result = bsm_greeks(
        spot_price=150.0,
        strike_price=100.0,
        time_to_expiry=1.0,
        risk_free_rate=0.05,
        implied_volatility=0.2,
        option_type="call",
    )

    assert "error" not in result
    assert result["delta"] > 0.9


def test_bsm_invalid_inputs():
    """Given invalid inputs, when computing Greeks, then error returned."""
    assert "error" in bsm_greeks(0, 100, 1, 0.05, 0.2)
    assert "error" in bsm_greeks(100, 100, -1, 0.05, 0.2)
    assert "error" in bsm_greeks(100, 100, 1, 0.05, 0.2, "invalid")


def test_gex_aggregate_basic():
    """Given mixed call/put positions, when aggregating GEX, then net GEX is computed."""
    positions = [
        {"spot_price": 450.0, "strike_price": 445.0, "time_to_expiry": 0.0833,
         "risk_free_rate": 0.05, "implied_volatility": 0.18, "option_type": "call",
         "quantity": 100, "open_interest": 5000},
        {"spot_price": 450.0, "strike_price": 455.0, "time_to_expiry": 0.0833,
         "risk_free_rate": 0.05, "implied_volatility": 0.18, "option_type": "put",
         "quantity": 100, "open_interest": 4000},
        {"spot_price": 450.0, "strike_price": 450.0, "time_to_expiry": 0.0833,
         "risk_free_rate": 0.05, "implied_volatility": 0.18, "option_type": "call",
         "quantity": 200, "open_interest": 8000},
    ]

    result = aggregate_gex(positions)

    assert "error" not in result
    assert "net_gex" in result
    assert "total_call_gamma" in result
    assert "total_put_gamma" in result
    assert "dealer_position_bias" in result
    assert result["dealer_position_bias"] in ("long_gamma", "short_gamma")
    assert result["n_positions"] == 3


def test_gex_empty_positions():
    """Given empty positions list, when aggregating GEX, then error returned."""
    assert "error" in aggregate_gex([])
