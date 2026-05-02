"""Econometrics router: ADF, Granger causality, OLS regression."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.econometrics.econometrics_service import adf_test, granger_causality, ols_regression

router = APIRouter()


class AdfRequest(BaseModel):
    series: list[float]
    max_lags: int | None = None


class GrangerRequest(BaseModel):
    x: list[float]
    y: list[float]
    max_lags: int = 10


class OlsRequest(BaseModel):
    y: list[float]
    x: list[list[float]]


@router.post("/adf")
def run_adf(req: AdfRequest):
    return adf_test(req.series, req.max_lags)


@router.post("/granger")
def run_granger(req: GrangerRequest):
    return granger_causality(req.x, req.y, req.max_lags)


@router.post("/ols")
def run_ols(req: OlsRequest):
    return ols_regression(req.y, req.x)
