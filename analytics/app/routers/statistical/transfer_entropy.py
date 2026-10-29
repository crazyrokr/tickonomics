from fastapi import APIRouter

from app.models.requests import TransferEntropyRequest
from app.models.responses import TransferEntropyResponse
from app.services.statistical.transfer_entropy_service import compute_transfer_entropy

router = APIRouter()


@router.post("/compute", response_model=TransferEntropyResponse)
async def compute(request: TransferEntropyRequest) -> TransferEntropyResponse:
    result = compute_transfer_entropy(request.source, request.target, request.lag, request.n_bootstraps)
    return TransferEntropyResponse(**result)
