"""Climate model router: stochastic liquidity simulation."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.climate.climate_service import simulate_climate

router = APIRouter()


class ClimateSimulateRequest(BaseModel):
    adaptation_finance_increase: float = 0.1
    carbon_tax: float = 50.0
    theta: float = 0.5
    mu: float = 0.02
    sigma: float = 0.15
    seasonal_amplitude: float = 0.05
    seasonal_period: float = 252.0
    n_steps: int = 252
    n_paths: int = 100
    seed: int | None = None


@router.post("/simulate")
def run_simulate(req: ClimateSimulateRequest):
    return simulate_climate(
        adaptation_finance_increase=req.adaptation_finance_increase,
        carbon_tax=req.carbon_tax,
        theta=req.theta,
        mu=req.mu,
        sigma=req.sigma,
        seasonal_amplitude=req.seasonal_amplitude,
        seasonal_period=req.seasonal_period,
        n_steps=req.n_steps,
        n_paths=req.n_paths,
        seed=req.seed,
    )
