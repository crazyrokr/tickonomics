"""Strategic run detection: volume-weighted price acceleration and Markov transition."""

import numpy as np


def detect_strategic_runs(
    prices: list[float],
    volumes: list[float],
    window: int = 20,
    acceleration_threshold: float = 2.0,
) -> dict:
    if len(prices) < window + 1:
        return {"error": f"At least {window + 1} price observations required"}

    if len(prices) != len(volumes):
        return {"error": "prices and volumes must have same length"}

    p = np.array(prices)
    v = np.array(volumes)

    returns = np.diff(p) / p[:-1]
    vwap_changes = []
    for i in range(window, len(returns)):
        w = v[i - window + 1: i + 1]
        w_sum = np.sum(w)
        if w_sum == 0:
            vwap_changes.append(0.0)
        else:
            prices_slice = p[i - window + 1: i + 1]
            vwap_current = float(np.average(prices_slice, weights=w))

            prev_w = v[i - window: i]
            prev_w_sum = np.sum(prev_w)
            if prev_w_sum == 0:
                vwap_changes.append(0.0)
            else:
                prev_prices = p[i - window: i]
                vwap_prev = float(np.average(prev_prices, weights=prev_w))
                vwap_changes.append((vwap_current - vwap_prev) / vwap_prev if vwap_prev != 0 else 0.0)

    vwap_arr = np.array(vwap_changes)
    if len(vwap_arr) < 2:
        return {"error": "Insufficient data for VWAP acceleration"}

    rolling_std = np.std(vwap_arr, ddof=1) if np.std(vwap_arr, ddof=1) > 0 else 1e-10
    acceleration = np.abs(vwap_arr[-1]) / rolling_std

    current_state = "aggressive" if acceleration > acceleration_threshold else "passive"

    states = ["passive" if np.abs(vc) / rolling_std <= acceleration_threshold else "aggressive" for vc in vwap_arr]

    pp_count = sum(1 for i in range(1, len(states)) if states[i - 1] == "passive" and states[i] == "passive")
    pa_count = sum(1 for i in range(1, len(states)) if states[i - 1] == "passive" and states[i] == "aggressive")
    ap_count = sum(1 for i in range(1, len(states)) if states[i - 1] == "aggressive" and states[i] == "passive")
    aa_count = sum(1 for i in range(1, len(states)) if states[i - 1] == "aggressive" and states[i] == "aggressive")

    p_total = pp_count + pa_count
    a_total = ap_count + aa_count

    transition_matrix = {
        "passive_to_passive": round(pp_count / p_total, 4) if p_total > 0 else 0.5,
        "passive_to_aggressive": round(pa_count / p_total, 4) if p_total > 0 else 0.5,
        "aggressive_to_passive": round(ap_count / a_total, 4) if a_total > 0 else 0.5,
        "aggressive_to_aggressive": round(aa_count / a_total, 4) if a_total > 0 else 0.5,
    }

    run_count = 0
    in_run = False
    for s in states[-20:]:
        if s == "aggressive" and not in_run:
            run_count += 1
            in_run = True
        elif s == "passive":
            in_run = False

    return {
        "current_state": current_state,
        "acceleration_score": round(float(acceleration), 4),
        "transition_matrix": transition_matrix,
        "run_count_20obs": run_count,
        "window": window,
        "n_observations": len(prices),
    }
