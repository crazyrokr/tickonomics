from pydantic import BaseModel
from typing import Optional


class EvtFitResponse(BaseModel):
    status: str
    shape_xi: Optional[float] = None
    scale_beta: Optional[float] = None
    threshold_u: Optional[float] = None
    tail_var_999: Optional[float] = None
    exceedance_count: Optional[int] = None
    message: Optional[str] = None


class FdrCorrectionResponse(BaseModel):
    actionable_mask: list[bool]
    adjusted_p_values: list[float]
    rejected_count: int
    discovery_reduction: float


class YieldCurveFitResponse(BaseModel):
    status: str
    beta_0: Optional[float] = None
    beta_1: Optional[float] = None
    beta_2: Optional[float] = None
    tau: Optional[float] = None
    covariance_trace: Optional[float] = None
    error: Optional[str] = None


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
