from pydantic import BaseModel, Field
from typing import Optional


class EvtFitRequest(BaseModel):
    data: list[float] = Field(..., min_length=1)
    quantile_u: float = Field(default=0.95, gt=0.0, lt=1.0)


class FdrCorrectionRequest(BaseModel):
    p_values: list[float] = Field(..., min_length=0)
    alpha: float = Field(default=0.05, gt=0.0, lt=1.0)


class YieldCurveFitRequest(BaseModel):
    maturities: list[float] = Field(..., min_length=2)
    yields: list[float] = Field(..., min_length=2)


class YieldCurveInterpolateRequest(BaseModel):
    betas: list[float] = Field(..., min_length=4, max_length=4)
    tau: float = Field(..., gt=0.0)
    maturities: list[float] = Field(..., min_length=1)


class MacroShockRequest(BaseModel):
    columns: list[str] = Field(..., min_length=1)
    data: list[list[float]] = Field(..., min_length=1)
    steps: int = Field(default=5, ge=1, le=50)


class QuantileRegressionRequest(BaseModel):
    x_data: list[list[float]] = Field(..., min_length=1)
    y_data: list[float] = Field(..., min_length=1)
    quantiles: list[float] = Field(default=[0.05, 0.95])


class TransferEntropyRequest(BaseModel):
    source: list[float] = Field(..., min_length=10)
    target: list[float] = Field(..., min_length=10)
    lag: int = Field(default=1, ge=1)
    n_bootstraps: int = Field(default=1000, ge=100)
