from fastapi import APIRouter

from app.models.requests import MacroShockRequest
from app.models.responses import MacroShockResponse
from app.services.statistical.macro_shock_service import compute_impulse_response

router = APIRouter()


@router.post("/impulse-response", response_model=MacroShockResponse)
async def impulse_response(request: MacroShockRequest) -> MacroShockResponse:
    result = compute_impulse_response(request.columns, request.data, request.steps)
    if "error" in result:
        return MacroShockResponse(
            horizon=[],
            response_paths={},
            confidence_high={},
            confidence_low={},
            shock_std_dev=0.0,
        )
    return MacroShockResponse(**result)
