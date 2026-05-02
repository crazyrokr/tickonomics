"""Benchmark tournament router: multi-model signal comparison."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.benchmark.tournament_service import evaluate_tournament

router = APIRouter()


class TournamentRequest(BaseModel):
    returns: list[float]
    seed: int | None = None


@router.post("/evaluate")
def run_tournament(req: TournamentRequest):
    return evaluate_tournament(req.returns, seed=req.seed)
