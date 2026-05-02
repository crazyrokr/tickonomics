"""Drift-diffusion router: Ito process simulation and barrier hitting."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.drift.drift_service import barrier_hitting_probability, simulate_ito

router = APIRouter()


class ItoSimulateRequest(BaseModel):
    mu: float = 0.001
    sigma: float = 0.02
    initial_value: float = 0.5
    t_max: float = 30.0
    n_steps: int = 252
    n_paths: int = 100
    seed: int | None = None


class BarrierRequest(BaseModel):
    mu: float = 0.001
    sigma: float = 0.02
    initial_value: float = 0.5
    barrier_level: float = 2.0
    t_max: float = 30.0
    n_steps: int = 252
    n_paths: int = 1000
    seed: int | None = None


@router.post("/simulate")
def run_simulate(req: ItoSimulateRequest):
    return simulate_ito(
        mu=req.mu,
        sigma=req.sigma,
        initial_value=req.initial_value,
        t_max=req.t_max,
        n_steps=req.n_steps,
        n_paths=req.n_paths,
        seed=req.seed,
    )


@router.post("/barrier")
def run_barrier(req: BarrierRequest):
    return barrier_hitting_probability(
        mu=req.mu,
        sigma=req.sigma,
        initial_value=req.initial_value,
        barrier_level=req.barrier_level,
        t_max=req.t_max,
        n_steps=req.n_steps,
        n_paths=req.n_paths,
        seed=req.seed,
    )
