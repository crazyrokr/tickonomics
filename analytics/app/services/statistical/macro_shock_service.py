import logging

import numpy as np
import pandas as pd
from statsmodels.tsa.statespace.varmax import VARMAX

logger = logging.getLogger(__name__)


def compute_impulse_response(columns: list[str], data: list[list[float]], steps: int = 5) -> dict:
    df = pd.DataFrame(data, columns=columns)
    try:
        model = VARMAX(df, order=(1, 0), trend="c")
        res = model.fit(maxiter=1000, disp=False)
        irf = res.impulse_responses(steps=steps, impulse=0)

        response_paths = {}
        confidence_high = {}
        confidence_low = {}
        for col in columns:
            response_paths[col] = irf[col].tolist()
            confidence_high[col] = (irf[col] * 1.96).tolist()
            confidence_low[col] = (irf[col] * -1.96).tolist()

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
