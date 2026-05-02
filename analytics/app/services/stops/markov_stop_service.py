"""Markov Stop Engine: optimal stop-loss and take-profit calibration."""

import numpy as np


def calibrate_stops(
    trade_pnl_series: list[float],
    max_iterations: int = 1000,
    learning_rate: float = 0.01,
    seed: int | None = None,
) -> dict:
    if len(trade_pnl_series) < 10:
        return {"error": "At least 10 trade PnL observations required"}

    rng = np.random.default_rng(seed)
    pnl = np.array(trade_pnl_series)
    n = len(pnl)

    mu = float(np.mean(pnl))
    sigma = float(np.std(pnl, ddof=1)) if np.std(pnl, ddof=1) > 0 else 1e-6

    stops = np.linspace(-3 * sigma, -0.5 * sigma, 50)
    targets = np.linspace(0.5 * sigma, 3 * sigma, 50)

    best_sharpe = -np.inf
    best_stop = -sigma
    best_target = sigma
    converged = False
    iterations = 0

    for _ in range(min(max_iterations, 2500)):
        iterations += 1
        sl = float(rng.choice(stops))
        tp = float(rng.choice(targets))

        equity = [0.0]
        for p in pnl:
            if p <= sl:
                equity.append(equity[-1] + sl)
            elif p >= tp:
                equity.append(equity[-1] + tp)
            else:
                equity.append(equity[-1] + p)

        returns = np.diff(equity)
        if len(returns) < 2 or np.std(returns, ddof=1) == 0:
            continue

        sharpe = float(np.mean(returns) / np.std(returns, ddof=1))

        if sharpe > best_sharpe:
            best_sharpe = sharpe
            best_stop = sl
            best_target = tp

            stops = np.linspace(min(sl - 0.5 * sigma, -1e-6), sl + 0.5 * sigma, 50)
            targets = np.linspace(max(tp - 0.5 * sigma, 1e-6), tp + 0.5 * sigma, 50)

        if iterations >= max_iterations:
            break

    converged = iterations < max_iterations

    decay = float(sigma / (abs(mu) + sigma))
    signal_drift = round(mu, 6)

    return {
        "optimal_stop_loss": round(best_stop, 6),
        "optimal_take_profit": round(best_target, 6),
        "signal_drift": round(signal_drift, 6),
        "decay_intensity": round(decay, 4),
        "best_sharpe": round(best_sharpe, 4),
        "converged": converged,
        "iterations": iterations,
    }
