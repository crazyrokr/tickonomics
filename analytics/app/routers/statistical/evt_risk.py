from fastapi import APIRouter

from app.models.requests import EvtFitRequest
from app.models.responses import EvtFitResponse
from app.services.statistical.evt_risk_service import EvtRiskService

router = APIRouter()
_evt_service = EvtRiskService()


@router.post("/fit", response_model=EvtFitResponse)
async def fit_tail_distribution(request: EvtFitRequest) -> EvtFitResponse:
    result = _evt_service.fit_tail_distribution(request.data, request.quantile_u)
    return EvtFitResponse(**result)


@router.post("/simulate")
async def simulate_tail_paths(shape_xi: float, scale_beta: float, n_paths: int = 1000):
    paths = _evt_service.simulate_tail_paths(shape_xi, scale_beta, n_paths)
    return {"paths": paths, "count": len(paths)}
