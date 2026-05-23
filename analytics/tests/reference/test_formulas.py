"""T4 known-answer tests for the reference formula module.

Each test uses code-level # Given / # When / # Then comments and verifies
against analytically trivial inputs.
"""

import numpy as np
import pytest

from tests.reference.formulas import (
    amihud_exact,
    bsm_call_price,
    bsm_delta,
    bsm_gamma,
    bsm_put_price,
    cvar_historical,
    fdr_bh_corrected,
    macaulay_duration_exact,
    convexity_exact,
    nelson_siegel_rate,
    sharpe_exact,
    sortino_exact,
    var_historical,
    zero_coupon_convexity,
    zero_coupon_duration,
)


class TestBSM:
    def test_atm_call_at_expiry(self):
        # Given: ATM call at expiry (T=0)
        # When: computing call price
        result = bsm_call_price(S=100, K=100, T=0, r=0.05, sigma=0.2)
        # Then: intrinsic value is 0
        assert abs(result - 0.0) < 1e-10

    def test_deep_itm_call(self):
        # Given: deep ITM call with near-zero volatility
        # When: computing call price
        result = bsm_call_price(S=200, K=100, T=1, r=0.0, sigma=0.01)
        # Then: price ≈ S - K = 100 (nearly deterministic)
        assert abs(result - 100.0) < 0.5

    def test_put_call_parity(self):
        # Given: standard BSM parameters
        S, K, T, r, sigma = 100, 105, 0.5, 0.05, 0.2
        # When: computing call and put prices
        call = bsm_call_price(S, K, T, r, sigma)
        put = bsm_put_price(S, K, T, r, sigma)
        # Then: call - put = S - K*exp(-rT) (parity identity)
        parity = S - K * np.exp(-r * T)
        assert abs((call - put) - parity) < 1e-6

    def test_atm_delta_range(self):
        # Given: ATM call with 1 year expiry
        # When: computing delta
        delta = bsm_delta(S=100, K=100, T=1, r=0.05, sigma=0.2, option_type="call")
        # Then: ATM delta is between 0.5 and 0.7
        assert 0.5 < delta < 0.7

    def test_put_delta_less_than_call(self):
        # Given: same parameters for call and put
        S, K, T, r, sigma = 100, 100, 1, 0.05, 0.2
        # When: computing both deltas
        call_delta = bsm_delta(S, K, T, r, sigma, "call")
        put_delta = bsm_delta(S, K, T, r, sigma, "put")
        # Then: call_delta - put_delta = 1 (BSM identity)
        assert abs((call_delta - put_delta) - 1.0) < 1e-10

    def test_gamma_positive(self):
        # Given: standard parameters
        # When: computing gamma
        gamma = bsm_gamma(S=100, K=100, T=1, r=0.05, sigma=0.2)
        # Then: gamma is always positive
        assert gamma > 0


class TestNelsonSiegel:
    def test_at_zero(self):
        # Given: NS parameters and t=0
        b0, b1, b2, tau = 0.05, -0.02, 0.01, 3.0
        # When: computing rate at t=0
        rate = nelson_siegel_rate(b0, b1, b2, tau, t=0)
        # Then: rate = b0 + b1 (limit as t→0)
        assert abs(rate - (b0 + b1)) < 1e-10

    def test_at_infinity(self):
        # Given: NS parameters and very large t
        b0, b1, b2, tau = 0.05, -0.02, 0.01, 3.0
        # When: computing rate at t=1000
        rate = nelson_siegel_rate(b0, b1, b2, tau, t=1000)
        # Then: rate ≈ b0 (limit as t→∞)
        assert abs(rate - b0) < 1e-4


class TestFixedIncome:
    def test_zero_coupon_duration(self):
        # Given: zero-coupon bond with T=5 years, y=5%
        cf = np.array([0, 0, 0, 0, 100.0])
        t = np.array([1.0, 2.0, 3.0, 4.0, 5.0])
        # When: computing Macaulay duration
        dur = macaulay_duration_exact(cf, t, 0.05)
        # Then: duration = 5.0 (exact identity for zero-coupon)
        assert abs(dur - 5.0) < 1e-10

    def test_zero_coupon_convexity(self):
        # Given: zero-coupon bond with T=5, y=5%
        # When: computing convexity
        conv = zero_coupon_convexity(5.0, 0.05)
        # Then: convexity = 5*6/1.05^2 ≈ 27.211
        expected = 5.0 * 6.0 / 1.05 ** 2
        assert abs(conv - expected) < 1e-6

    def test_zero_coupon_duration_identity(self):
        # Given: zero-coupon bond
        # When: computing via identity function
        # Then: duration = maturity
        assert abs(zero_coupon_duration(5.0) - 5.0) < 1e-10


class TestAmihud:
    def test_known_values(self):
        # Given: known returns and volumes
        returns = np.array([0.01, -0.02, 0.03])
        volumes = np.array([1e6, 2e6, 3e6])
        # When: computing Amihud measure
        result = amihud_exact(returns, volumes)
        # Then: matches manual computation
        expected = np.mean(np.abs(returns) / volumes)
        assert abs(result - expected) < 1e-12


class TestSharpeSortino:
    def test_sharpe_known_values(self):
        # Given: constant returns of 0.01 with rf=0
        returns = np.full(252, 0.01)
        # When: computing Sharpe (non-annualized)
        result = sharpe_exact(returns, rf=0.0, annualize=False)
        # Then: std=0 so Sharpe=0 (edge case handled)
        assert result == 0.0

    def test_sortino_all_positive_no_downside(self):
        # Given: all positive excess returns
        returns = np.array([0.01, 0.02, 0.03])
        # When: computing Sortino
        result = sortino_exact(returns, rf=0.0, annualize=False)
        # Then: no downside → returns 0
        assert result == 0.0


class TestFDR:
    def test_all_significant(self):
        # Given: all tiny p-values
        p = np.array([0.001, 0.002, 0.003])
        # When: applying BH correction at alpha=0.05
        result = fdr_bh_corrected(p, alpha=0.05)
        # Then: all rejected
        assert all(result)

    def test_none_significant(self):
        # Given: all large p-values
        p = np.array([0.5, 0.6, 0.7, 0.8])
        # When: applying BH correction at alpha=0.05
        result = fdr_bh_corrected(p, alpha=0.05)
        # Then: none rejected
        assert not any(result)


class TestVaR:
    def test_var_known_distribution(self):
        # Given: known return array
        returns = np.array([-0.05, -0.03, -0.01, 0.01, 0.03])
        # When: computing VaR at 80% confidence
        result = var_historical(returns, confidence=0.8)
        # Then: 20th percentile = -0.034, VaR = 0.034
        assert abs(result - 0.034) < 1e-10

    def test_cvar_geq_var(self):
        # Given: random returns
        np.random.seed(42)
        returns = np.random.normal(0, 0.01, 1000)
        # When: computing VaR and CVaR
        var = var_historical(returns, 0.99)
        cvar = cvar_historical(returns, 0.99)
        # Then: CVaR >= VaR (monotonicity)
        assert cvar >= var - 1e-10
