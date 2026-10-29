"""Online optimizer router: SGD weight-delta computation."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.optimizer.sgd_optimizer_service import compute_weight_delta

router = APIRouter()


class WeightDeltaRequest(BaseModel):
    current_weights: dict[str, float]
    signal_performance: list[dict[str, float]]
    learning_rate: float = 0.01
    lookback_days: int | None = None
    max_delta: float = 0.05


@router.post("/weight-delta")
def run_weight_delta(req: WeightDeltaRequest):
    return compute_weight_delta(
        current_weights=req.current_weights,
        signal_performance=req.signal_performance,
        learning_rate=req.learning_rate,
        lookback_days=req.lookback_days,
        max_delta=req.max_delta,
    )
