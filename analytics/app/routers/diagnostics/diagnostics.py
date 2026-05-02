"""Diagnostics router: QQ-plot, ACF, convergence analysis."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.diagnostics.diagnostic_service import compute_diagnostics

router = APIRouter()


class DiagnosticsRequest(BaseModel):
    returns: list[float]


@router.post("/stats")
def run_diagnostics(req: DiagnosticsRequest):
    return compute_diagnostics(req.returns)
