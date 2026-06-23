import numpy as np
import pandas as pd
import statsmodels.api as sm
from statsmodels.regression.quantile_regression import QuantReg


def calculate_quantile_bands(
        x_data: list[list[float]], y_data: list[float], quantiles: list[float] | None = None
) -> dict:
    if quantiles is None:
        quantiles = [0.05, 0.95]

    x_df = pd.DataFrame(x_data)
    x_with_intercept = sm.add_constant(x_df)
    y_series = pd.Series(y_data)

    coefficients = []
    pseudo_r2 = []

    for q in quantiles:
        model = QuantReg(y_series, x_with_intercept)
        res = model.fit(q=q)
        coefficients.append(res.params.tolist())
        pseudo_r2.append(float(res.prsquared))

    return {
        "quantiles": quantiles,
        "coefficients": coefficients,
        "pseudo_r2": pseudo_r2,
    }
