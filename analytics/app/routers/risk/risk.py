"""Risk metrics router: VaR, CVaR, GARCH."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.risk.risk_service import value_at_risk, conditional_var, garch_forecast

router = APIRouter()


class VarRequest(BaseModel):
    returns: list[float]
    confidence: float = 0.99
    method: str = "historical"


class CvarRequest(BaseModel):
    returns: list[float]
    confidence: float = 0.99


class GarchRequest(BaseModel):
    returns: list[float]
    p: int = 1
    q: int = 1
    horizon: int = 5


@router.post("/var")
def run_var(req: VarRequest):
    return value_at_risk(req.returns, req.confidence, req.method)


@router.post("/cvar")
def run_cvar(req: CvarRequest):
    return conditional_var(req.returns, req.confidence)


@router.post("/garch")
def run_garch(req: GarchRequest):
    return garch_forecast(req.returns, req.p, req.q, req.horizon)
