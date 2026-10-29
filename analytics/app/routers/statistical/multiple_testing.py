from fastapi import APIRouter

from app.models.requests import FdrCorrectionRequest
from app.models.responses import FdrCorrectionResponse
from app.services.statistical.multiple_testing_service import apply_fdr_correction

router = APIRouter()


@router.post("/correct", response_model=FdrCorrectionResponse)
async def correct_p_values(request: FdrCorrectionRequest) -> FdrCorrectionResponse:
    result = apply_fdr_correction(request.p_values, request.alpha)
    return FdrCorrectionResponse(**result)
