"""Closed-form reference implementations for T4 known-answer test oracles.

Each function is pure (no side effects, no RNG, no state) and cites its source.
Uses only numpy and scipy.stats.norm.
"""

import numpy as np
from scipy.stats import norm


def bsm_call_price(S: float, K: float, T: float, r: float, sigma: float) -> float:
    """Black-Scholes call price. Hull, Options Futures and Other Derivatives, Ch.21."""
    if T <= 0:
        return max(S - K, 0.0)
    d1 = (np.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * np.sqrt(T))
    d2 = d1 - sigma * np.sqrt(T)
    return float(S * norm.cdf(d1) - K * np.exp(-r * T) * norm.cdf(d2))


def bsm_put_price(S: float, K: float, T: float, r: float, sigma: float) -> float:
    """Put price via put-call parity. Hull Ch.21: put = call - S + K*exp(-rT)."""
    call = bsm_call_price(S, K, T, r, sigma)
    return float(call - S + K * np.exp(-r * T))


def bsm_delta(S: float, K: float, T: float, r: float, sigma: float, option_type: str = "call") -> float:
    """BSM delta: N(d1) for call, N(d1)-1 for put. Hull Ch.21."""
    if T <= 0:
        if option_type == "put":
            return -1.0 if S < K else 0.0
        return 1.0 if S > K else 0.0
    d1 = (np.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * np.sqrt(T))
    if option_type == "put":
        return float(norm.cdf(d1) - 1.0)
    return float(norm.cdf(d1))


def bsm_gamma(S: float, K: float, T: float, r: float, sigma: float) -> float:
    """BSM gamma: n(d1) / (S * sigma * sqrt(T)). Hull Ch.21."""
    if T <= 0 or S <= 0 or sigma <= 0:
        return 0.0
    d1 = (np.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * np.sqrt(T))
    return float(norm.pdf(d1) / (S * sigma * np.sqrt(T)))


def nelson_siegel_rate(b0: float, b1: float, b2: float, tau: float, t: float) -> float:
    """Nelson-Siegel yield curve: b0 + (b1+b2)*(1-exp(-t/tau))/(t/tau) - b2*exp(-t/tau).
    Nelson & Siegel (1987), 'Parsimonious Modeling of Yield Curves'.
    """
    if t <= 0:
        return b0 + b1
    x = t / tau
    exp_term = np.exp(-x)
    term1 = (1 - exp_term) / x
    term2 = term1 - exp_term
    return float(b0 + b1 * term1 + b2 * term2)


def gpd_quantile(xi: float, beta: float, exceedance_prob: float, threshold: float,
                 n_total: int, n_exceed: int) -> float:
    """GPD quantile: threshold + (beta/xi)*((n_total/n_exceed * p)^(-xi) - 1).
    Embrechts et al., 'Modelling Extremal Events', Ch.6.
    """
    rate = n_total / n_exceed
    return float(threshold + (beta / xi) * ((rate * exceedance_prob) ** (-xi) - 1))


def macaulay_duration_exact(cash_flows: np.ndarray, times: np.ndarray, y: float) -> float:
    """Macaulay duration: sum(t*PV(CF)) / sum(PV(CF)). Fabozzi, Fixed Income Analysis, Ch.6."""
    discount_factors = (1 + y) ** times
    pv_cf = cash_flows / discount_factors
    price = np.sum(pv_cf)
    return float(np.sum(times * pv_cf) / price)


def convexity_exact(cash_flows: np.ndarray, times: np.ndarray, y: float) -> float:
    """Convexity: sum(t*(t+1)*PV(CF)) / ((1+y)^2 * sum(PV(CF))). Fabozzi Ch.6."""
    discount_factors = (1 + y) ** times
    pv_cf = cash_flows / discount_factors
    price = np.sum(pv_cf)
    return float(np.sum(times * (times + 1) * pv_cf) / (price * (1 + y) ** 2))


def zero_coupon_duration(t: float) -> float:
    """Zero-coupon bond duration = t (identity)."""
    return t


def zero_coupon_convexity(t: float, y: float) -> float:
    """Zero-coupon convexity: t*(t+1) / (1+y)^2."""
    return float(t * (t + 1) / (1 + y) ** 2)


def amihud_exact(returns: np.ndarray, dollar_volumes: np.ndarray) -> float:
    """Amihud illiquidity: mean(|return| / dollar_volume). Amihud (2002), JFM."""
    valid = dollar_volumes > 0
    return float(np.mean(np.abs(returns[valid]) / dollar_volumes[valid]))


def sharpe_exact(returns: np.ndarray, rf: float = 0.0, annualize: bool = True,
                 periods_per_year: int = 252) -> float:
    """Sharpe ratio: mean(R-rf) / std(R-rf) * sqrt(periods). Sharpe (1966)."""
    excess = returns - rf
    mean_excess = np.mean(excess)
    std_excess = np.std(returns, ddof=1)
    if std_excess == 0:
        return 0.0
    ratio = mean_excess / std_excess
    if annualize:
        ratio *= np.sqrt(periods_per_year)
    return float(ratio)


def sortino_exact(returns: np.ndarray, rf: float = 0.0, annualize: bool = True,
                  periods_per_year: int = 252) -> float:
    """Sortino ratio: mean(R-rf) / downside_deviation * sqrt(periods). Sortino & Price (1994)."""
    excess = returns - rf
    downside = excess[excess < 0]
    if len(downside) == 0:
        return 0.0
    downside_std = np.sqrt(np.mean(downside ** 2))
    if downside_std == 0:
        return 0.0
    ratio = np.mean(excess) / downside_std
    if annualize:
        ratio *= np.sqrt(periods_per_year)
    return float(ratio)


def fdr_bh_corrected(p_values: np.ndarray, alpha: float = 0.05) -> list[bool]:
    """Benjamini-Hochberg step-up procedure. Benjamini & Hochberg (1995), JRSS-B."""
    m = len(p_values)
    if m == 0:
        return []
    sorted_indices = np.argsort(p_values)
    sorted_p = p_values[sorted_indices]
    rejected = np.zeros(m, dtype=bool)
    threshold = 0.0
    for i in range(m - 1, -1, -1):
        if sorted_p[i] <= alpha * (i + 1) / m:
            threshold = sorted_p[i]
            rejected[:i + 1] = True
            break
    result = np.zeros(m, dtype=bool)
    result[sorted_indices] = rejected
    return result.tolist()


def var_historical(returns: np.ndarray, confidence: float) -> float:
    """Historical VaR: percentile(returns, 100*(1-confidence))."""
    return float(-np.percentile(returns, (1 - confidence) * 100))


def cvar_historical(returns: np.ndarray, confidence: float) -> float:
    """CVaR: mean(returns[returns <= -VaR])."""
    var = -np.percentile(returns, (1 - confidence) * 100)
    return float(-np.mean(returns[returns <= -var]))
