"""Volatility forecast router: multi-horizon GARCH-based forecast."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.ml.volatility_forecast_service import forecast_volatility

router = APIRouter()


class VolatilityForecastRequest(BaseModel):
    returns: list[float]
    horizon_days: int = 5
    model: str = "garch"


@router.post("/volatility-forecast")
def run_forecast(req: VolatilityForecastRequest):
    return forecast_volatility(req.returns, req.horizon_days, req.model)
