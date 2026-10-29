"""Greeks router: BSM option Greeks and GEX aggregation."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.greeks.bsm_service import aggregate_gex, bsm_greeks

router = APIRouter()


class BsmGreeksRequest(BaseModel):
    spot_price: float
    strike_price: float
    time_to_expiry: float
    risk_free_rate: float
    implied_volatility: float
    option_type: str = "call"


class GexAggregateRequest(BaseModel):
    positions: list[dict]


@router.post("/bsm")
def run_bsm_greeks(req: BsmGreeksRequest):
    return bsm_greeks(
        spot_price=req.spot_price,
        strike_price=req.strike_price,
        time_to_expiry=req.time_to_expiry,
        risk_free_rate=req.risk_free_rate,
        implied_volatility=req.implied_volatility,
        option_type=req.option_type,
    )


@router.post("/gex-aggregate")
def run_gex_aggregate(req: GexAggregateRequest):
    return aggregate_gex(req.positions)
