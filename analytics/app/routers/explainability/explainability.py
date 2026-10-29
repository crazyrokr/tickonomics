"""Explainability router: SHAP-style feature importance."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.explainability.shap_service import compute_feature_importance

router = APIRouter()


class FeatureImportanceRequest(BaseModel):
    model: str = "ili_signal"
    features: dict[str, float]
    seed: int | None = None


@router.post("/feature-importance")
def run_feature_importance(req: FeatureImportanceRequest):
    return compute_feature_importance(req.model, req.features, seed=req.seed)
