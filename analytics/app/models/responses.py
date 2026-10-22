
from pydantic import BaseModel


class EvtFitResponse(BaseModel):
    status: str
    shape_xi: float | None = None
    scale_beta: float | None = None
    threshold_u: float | None = None
    tail_var_999: float | None = None
    exceedance_count: int | None = None
    message: str | None = None


class FdrCorrectionResponse(BaseModel):
    actionable_mask: list[bool]
    adjusted_p_values: list[float]
    rejected_count: int
    discovery_reduction: float


class YieldCurveFitResponse(BaseModel):
    status: str
    beta_0: float | None = None
    beta_1: float | None = None
    beta_2: float | None = None
    tau: float | None = None
    covariance_trace: float | None = None
    error: str | None = None


class YieldCurveInterpolateResponse(BaseModel):
    maturities: list[float]
    yields: list[float]


class MacroShockResponse(BaseModel):
    horizon: list[int]
    response_paths: dict
    confidence_high: dict
    confidence_low: dict
    shock_std_dev: float


class QuantileRegressionResponse(BaseModel):
    quantiles: list[float]
    coefficients: list[list[float]]
    pseudo_r2: list[float]


class TransferEntropyResponse(BaseModel):
    entropy_bits: float
    p_value: float
    lag: int
    is_significant: bool


class HealthResponse(BaseModel):
    status: str
    scipy_version: str
    statsmodels_version: str
    numpy_version: str
