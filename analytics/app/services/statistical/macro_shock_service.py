import logging

import numpy as np
import pandas as pd
from statsmodels.tsa.statespace.varmax import VARMAX

logger = logging.getLogger(__name__)


def compute_impulse_response(columns: list[str], data: list[list[float]], steps: int = 5,
                             n_bootstrap: int = 200, seed: int = 42) -> dict:
    df = pd.DataFrame(data, columns=columns)
    try:
        model = VARMAX(df, order=(1, 0), trend="c")
        res = model.fit(maxiter=1000, disp=False)
        irf = res.impulse_responses(steps=steps, impulse=0)

        rng = np.random.default_rng(seed)
        fitted = res.fittedvalues
        resid = res.resid.values
        boot_irfs = []

        for _ in range(n_bootstrap):
            boot_idx = rng.choice(len(resid), size=len(resid), replace=True)
            boot_resid = resid[boot_idx]
            boot_data = fitted.values + boot_resid
            try:
                boot_model = VARMAX(pd.DataFrame(boot_data, columns=columns),
                                    order=(1, 0), trend="c")
                boot_res = boot_model.fit(maxiter=200, disp=False)
                boot_irf = boot_res.impulse_responses(steps=steps, impulse=0)
                boot_irfs.append(boot_irf)
            except Exception:
                continue

        response_paths = {}
        confidence_high = {}
        confidence_low = {}
        for col in columns:
            response_paths[col] = irf[col].tolist()
            if boot_irfs:
                boot_array = np.array([b[col].values for b in boot_irfs])
                confidence_high[col] = np.percentile(boot_array, 97.5, axis=0).tolist()
                confidence_low[col] = np.percentile(boot_array, 2.5, axis=0).tolist()
            else:
                se = irf[col].std()
                confidence_high[col] = (irf[col] + 1.96 * se).tolist()
                confidence_low[col] = (irf[col] - 1.96 * se).tolist()

        return {
            "horizon": list(range(len(irf))),
            "response_paths": response_paths,
            "confidence_high": confidence_high,
            "confidence_low": confidence_low,
            "shock_std_dev": 1.0,
        }
    except Exception as e:
        logger.error("SVAR fit failed: %s", e)
        return {"error": str(e)}
