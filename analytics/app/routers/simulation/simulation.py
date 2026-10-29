"""Simulation router: Sobol Monte Carlo and discrete monitoring correction."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.simulation.sobol_service import discrete_correction, sobol_simulate

router = APIRouter()


class SobolRequest(BaseModel):
    payoff_type: str = "call"
    s0: float = 100.0
    k: float = 100.0
    t: float = 1.0
    r: float = 0.05
    sigma: float = 0.2
    n_paths: int = 1024
    n_steps: int = 1
    seed: int | None = None


class DiscreteCorrectionRequest(BaseModel):
    continuous_price: float
    n_monitoring: int
    s0: float = 100.0
    k: float = 100.0
    t: float = 1.0
    r: float = 0.05
    sigma: float = 0.2


@router.post("/sobol")
def run_sobol(req: SobolRequest):
    return sobol_simulate(
        payoff_type=req.payoff_type,
        s0=req.s0,
        k=req.k,
        t=req.t,
        r=req.r,
        sigma=req.sigma,
        n_paths=req.n_paths,
        n_steps=req.n_steps,
        seed=req.seed,
    )


@router.post("/discrete-correction")
def run_discrete_correction(req: DiscreteCorrectionRequest):
    return discrete_correction(
        continuous_price=req.continuous_price,
        n_monitoring=req.n_monitoring,
        s0=req.s0,
        k=req.k,
        t=req.t,
        r=req.r,
        sigma=req.sigma,
    )
