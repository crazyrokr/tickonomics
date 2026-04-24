import numpy as np
from scipy.optimize import curve_fit


def nelson_siegel(t, b0, b1, b2, tau):
    exp_term = np.exp(-t / tau)
    term1 = (1 - exp_term) / (t / tau)
    term2 = term1 - exp_term
    return b0 + b1 * term1 + b2 * term2


def fit_yield_curve(maturities: list[float], yields: list[float]) -> dict:
    mat = np.array(maturities)
    yld = np.array(yields)
    p0 = [yld[-1], yld[0] - yld[-1], 0.02, 1.5]
    try:
        popt, pcov = curve_fit(nelson_siegel, mat, yld, p0=p0)
        return {
            "status": "SUCCESS",
            "beta_0": float(popt[0]),
            "beta_1": float(popt[1]),
            "beta_2": float(popt[2]),
            "tau": float(popt[3]),
            "covariance_trace": float(np.trace(pcov)),
        }
    except Exception as e:
        return {"status": "FAIL", "error": str(e)}


def interpolate_yield_curve(betas: list[float], tau: float, maturities: list[float]) -> list[float]:
    return nelson_siegel(np.array(maturities), betas[0], betas[1], betas[2], tau).tolist()
