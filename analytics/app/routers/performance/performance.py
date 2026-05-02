"""Performance analytics router: Sharpe, Sortino, Fama-French."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.performance.performance_service import fama_french_regression, sharpe_ratio, sortino_ratio

router = APIRouter()


class SharpeRequest(BaseModel):
    returns: list[float]
    risk_free_rate: float = 0.0
    annualize: bool = True


class SortinoRequest(BaseModel):
    returns: list[float]
    risk_free_rate: float = 0.0
    annualize: bool = True


class FamaFrenchRequest(BaseModel):
    returns: list[float]
    market_returns: list[float]
    smb: list[float] | None = None
    hml: list[float] | None = None


@router.post("/sharpe")
def run_sharpe(req: SharpeRequest):
    return sharpe_ratio(req.returns, req.risk_free_rate, req.annualize)


@router.post("/sortino")
def run_sortino(req: SortinoRequest):
    return sortino_ratio(req.returns, req.risk_free_rate, req.annualize)


@router.post("/fama-french")
def run_fama_french(req: FamaFrenchRequest):
    return fama_french_regression(req.returns, req.market_returns, req.smb, req.hml)
