"""Reproducibility router: RDS scoring."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.reproducibility.rds_scorer_service import compute_rds

router = APIRouter()


class RdsScoreRequest(BaseModel):
    model_name: str
    has_code: bool = False
    code_versioned: bool = False
    dataset_available: bool = False
    hyperparams_documented: bool = False
    results_reproducible: bool = False


@router.post("/score")
def run_rds_score(req: RdsScoreRequest):
    return compute_rds(
        model_name=req.model_name,
        has_code=req.has_code,
        code_versioned=req.code_versioned,
        dataset_available=req.dataset_available,
        hyperparams_documented=req.hyperparams_documented,
        results_reproducible=req.results_reproducible,
    )
