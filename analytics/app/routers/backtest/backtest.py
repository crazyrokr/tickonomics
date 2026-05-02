"""Backtest router: robustness parameter scan."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.backtest.robustness_service import robustness_scan

router = APIRouter()


class RobustnessScanRequest(BaseModel):
    strategy_returns: list[float]
    parameter_ranges: dict[str, list[float]]
    n_samples: int = 128
    seed: int | None = None


@router.post("/robustness-scan")
def run_robustness_scan(req: RobustnessScanRequest):
    return robustness_scan(
        strategy_returns=req.strategy_returns,
        parameter_ranges=req.parameter_ranges,
        n_samples=req.n_samples,
        seed=req.seed,
    )
