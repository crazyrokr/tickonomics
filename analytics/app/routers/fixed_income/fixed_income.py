"""Fixed income router: duration, convexity, YTM, Q-World fair value, T-Bill Greeks."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.fi.q_world_pricer_service import compute_fair_value, compute_tbill_greeks
from app.services.fixed_income.fixed_income_service import convexity, macaulay_duration, yield_to_maturity

router = APIRouter()


class DurationRequest(BaseModel):
    cash_flows: list[float]
    times: list[float]
    yield_rate: float


class ConvexityRequest(BaseModel):
    cash_flows: list[float]
    times: list[float]
    yield_rate: float


class YtmRequest(BaseModel):
    cash_flows: list[float]
    times: list[float]
    price: float
    guess: float = 0.05


class QWorldFairValueRequest(BaseModel):
    instrument: str = "3M_TBILL"
    current_yield: float
    lookback_days: int = 252


class TbillGreeksRequest(BaseModel):
    yield_level: float = 0.04
    duration_years: float = 0.25


@router.post("/duration")
def run_duration(req: DurationRequest):
    return macaulay_duration(req.cash_flows, req.times, req.yield_rate)


@router.post("/convexity")
def run_convexity(req: ConvexityRequest):
    return convexity(req.cash_flows, req.times, req.yield_rate)


@router.post("/ytm")
def run_ytm(req: YtmRequest):
    return yield_to_maturity(req.cash_flows, req.times, req.price, req.guess)


@router.post("/q-world-fair-value")
def run_q_world_fair_value(req: QWorldFairValueRequest):
    return compute_fair_value(req.instrument, req.current_yield, req.lookback_days)


@router.post("/tbill-greeks")
def run_tbill_greeks(req: TbillGreeksRequest):
    return compute_tbill_greeks(req.yield_level, req.duration_years)
