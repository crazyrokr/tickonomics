"""Markov Stop Engine router: optimal stop-loss/take-profit calibration."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.stops.markov_stop_service import calibrate_stops

router = APIRouter()


class CalibrateStopsRequest(BaseModel):
    trade_pnl_series: list[float]
    max_iterations: int = 1000
    seed: int | None = None


@router.post("/calibrate")
def run_calibrate(req: CalibrateStopsRequest):
    return calibrate_stops(req.trade_pnl_series, req.max_iterations, seed=req.seed)
