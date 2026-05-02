"""Liquidity router: comovement factor, Amihud measure, strategic runs."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.liquidity.amihud_service import compute_amihud
from app.services.liquidity.comovement_pca_service import compute_comovement_factor
from app.services.liquidity.strategic_runs_service import detect_strategic_runs

router = APIRouter()


class ComovementRequest(BaseModel):
    spread_matrix: list[list[float]]
    n_symbols: int | None = None


class AmihudRequest(BaseModel):
    returns: list[float]
    dollar_volumes: list[float]


class StrategicRunsRequest(BaseModel):
    prices: list[float]
    volumes: list[float]
    window: int = 20
    acceleration_threshold: float = 2.0


@router.post("/comovement-factor")
def run_comovement(req: ComovementRequest):
    return compute_comovement_factor(req.spread_matrix, req.n_symbols)


@router.post("/amihud")
def run_amihud(req: AmihudRequest):
    return compute_amihud(req.returns, req.dollar_volumes)


@router.post("/strategic-runs")
def run_strategic_runs(req: StrategicRunsRequest):
    return detect_strategic_runs(req.prices, req.volumes, req.window, req.acceleration_threshold)
