"""Perspective-mismatch router (ADR-037). Receives recent Polymarket quotes + OSINT events and returns
the deterministic divergence verdicts that the Java signal pipeline gates and broadcasts."""

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.services.perspex.perspex_service import analyze_perspex

router = APIRouter()


class PerspexQuote(BaseModel):
    market_id: str
    question: str
    outcome_yes_price: float = Field(..., ge=0.0, le=1.0)
    volume: float = Field(default=0.0, ge=0.0)
    liquidity: float = Field(default=0.0, ge=0.0)
    time: str


class PerspexNewsEvent(BaseModel):
    event_id: str
    headline: str
    avg_tone: float = 0.0
    time: str


class PerspexAnalyzeRequest(BaseModel):
    quotes: list[PerspexQuote] = Field(default_factory=list)
    events: list[PerspexNewsEvent] = Field(default_factory=list)
    divergence_threshold: float = Field(default=0.25, gt=0.0, lt=1.0)
    min_liquidity: float = Field(default=1000.0, ge=0.0)
    min_volume: float = Field(default=500.0, ge=0.0)


@router.post("/analyze")
def run_perspex(req: PerspexAnalyzeRequest):
    return analyze_perspex(
        [q.model_dump() for q in req.quotes],
        [e.model_dump() for e in req.events],
        req.divergence_threshold,
        req.min_liquidity,
        req.min_volume,
    )
