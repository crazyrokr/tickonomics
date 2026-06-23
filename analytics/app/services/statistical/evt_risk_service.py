import logging

import numpy as np
from scipy.stats import genpareto

logger = logging.getLogger(__name__)


class EvtRiskService:
    """Peak-over-threshold GPD fitting for tail risk. Reference: Rahaman (2026), Section 4.2."""

    def fit_tail_distribution(self, data: list[float], quantile_u: float = 0.95) -> dict:
        series = np.array(data)
        if len(series) < 100:
            return {"status": "ERROR", "message": "Sample size too small for EVT"}

        u = np.quantile(series, quantile_u)
        exceedances = series[series > u] - u

        if len(exceedances) < 10:
            return {"status": "ERROR", "message": "Insufficient tail observations"}

        try:
            xi, _, beta = genpareto.fit(exceedances)
        except Exception as e:
            logger.error("GPD fit failed: %s", e)
            return {"status": "FIT_FAILURE", "message": str(e)}

        n = len(series)
        nu = len(exceedances)
        p = 0.999

        # Gumbel fallback: when xi ≈ 0, the GPD limit is the Gumbel distribution.
        # The standard GPD VaR formula divides by xi and is numerically unstable for |xi| < 1e-6.
        # Reference: Coles (2001), Section 4.3.3; Embrechts, Klüppelberg & Mikosch (1997).
        if abs(xi) < 1e-6:
            tail_var = u - beta * np.log((n / nu) * (1 - p))
        else:
            tail_var = u + (beta / xi) * (((n / nu) * (1 - p)) ** (-xi) - 1)

        return {
            "status": "SUCCESS",
            "shape_xi": float(xi),
            "scale_beta": float(beta),
            "threshold_u": float(u),
            "tail_var_999": float(tail_var),
            "exceedance_count": int(nu),
        }

    def simulate_tail_paths(self, shape_xi: float, scale_beta: float, n_paths: int = 1000) -> list[float]:
        return genpareto.rvs(shape_xi, scale=scale_beta, size=n_paths).tolist()
