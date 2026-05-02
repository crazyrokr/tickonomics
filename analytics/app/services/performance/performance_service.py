"""Performance analytics: Fama-French factor regression, Sharpe ratio, Sortino ratio."""

import numpy as np
from scipy import stats


def sharpe_ratio(returns: list[float], risk_free_rate: float = 0.0, annualize: bool = True) -> dict:
    if len(returns) < 2:
        return {"error": "At least 2 returns required"}

    r = np.array(returns)
    excess = r - risk_free_rate / 252 if annualize else r - risk_free_rate
    mean_excess = np.mean(excess)
    std = np.std(r, ddof=1)

    if std == 0:
        return {"error": "Zero standard deviation"}

    sharpe = mean_excess / std
    ann_factor = np.sqrt(252) if annualize else 1.0

    return {
        "sharpe_ratio": round(float(sharpe * ann_factor), 4),
        "annualized_sharpe": round(float(sharpe * ann_factor), 4),
        "mean_return": round(float(np.mean(r)), 6),
        "std_dev": round(float(std), 6),
        "n_observations": len(returns),
    }


def sortino_ratio(returns: list[float], risk_free_rate: float = 0.0, annualize: bool = True) -> dict:
    if len(returns) < 2:
        return {"error": "At least 2 returns required"}

    r = np.array(returns)
    target = risk_free_rate / 252 if annualize else risk_free_rate
    excess = r - target
    downside = excess[excess < 0]

    if len(downside) == 0:
        return {"error": "No downside returns to compute Sortino"}

    downside_std = np.sqrt(np.mean(downside ** 2))
    if downside_std == 0:
        return {"error": "Zero downside deviation"}

    sortino = np.mean(excess) / downside_std
    ann_factor = np.sqrt(252) if annualize else 1.0

    return {
        "sortino_ratio": round(float(sortino * ann_factor), 4),
        "downside_deviation": round(float(downside_std), 6),
        "mean_return": round(float(np.mean(r)), 6),
        "n_negative": len(downside),
        "n_observations": len(returns),
    }


def fama_french_regression(
    returns: list[float],
    market_returns: list[float],
    smb: list[float] | None = None,
    hml: list[float] | None = None,
) -> dict:
    if len(returns) < 20:
        return {"error": "At least 20 observations required"}
    if len(returns) != len(market_returns):
        return {"error": "returns and market_returns must have equal length"}

    y = np.array(returns)
    n = len(y)
    factors = [np.array(market_returns)]

    if smb is not None:
        if len(smb) != n:
            return {"error": "SMB must have same length as returns"}
        factors.append(np.array(smb))
    if hml is not None:
        if len(hml) != n:
            return {"error": "HML must have same length as returns"}
        factors.append(np.array(hml))

    x = np.column_stack([np.ones(n)] + factors)
    beta = np.linalg.lstsq(x, y, rcond=None)[0]
    resid = y - x @ beta
    ssr = np.sum(resid ** 2)
    tss = np.sum((y - np.mean(y)) ** 2)
    r_squared = 1.0 - ssr / tss if tss > 0 else 0.0
    adj_r_squared = 1.0 - (1.0 - r_squared) * (n - 1) / (n - x.shape[1])

    mse = ssr / (n - x.shape[1])
    se = np.sqrt(np.diag(mse * np.linalg.inv(x.T @ x)))
    t_stats = beta / se
    p_values = 2.0 * (1.0 - stats.t.cdf(np.abs(t_stats), n - x.shape[1]))

    labels = ["alpha", "market_beta"]
    if smb is not None:
        labels.append("smb_beta")
    if hml is not None:
        labels.append("hml_beta")

    coefficients = []
    for i, label in enumerate(labels):
        coefficients.append({
            "name": label,
            "estimate": round(float(beta[i]), 6),
            "std_error": round(float(se[i]), 6),
            "t_statistic": round(float(t_stats[i]), 4),
            "p_value": round(float(p_values[i]), 4),
        })

    return {
        "model": "Fama-French " + ("3-factor" if (smb is not None and hml is not None) else "CAPM"),
        "coefficients": coefficients,
        "r_squared": round(float(r_squared), 4),
        "adjusted_r_squared": round(float(adj_r_squared), 4),
        "residual_std_error": round(float(np.sqrt(mse)), 6),
        "n_observations": n,
    }
