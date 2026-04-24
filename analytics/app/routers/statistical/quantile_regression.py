from fastapi import APIRouter

from app.models.requests import QuantileRegressionRequest
from app.models.responses import QuantileRegressionResponse
from app.services.statistical.quantile_regression_service import calculate_quantile_bands

router = APIRouter()


@router.post("/bands", response_model=QuantileRegressionResponse)
async def quantile_bands(request: QuantileRegressionRequest) -> QuantileRegressionResponse:
    result = calculate_quantile_bands(request.x_data, request.y_data, request.quantiles)
    return QuantileRegressionResponse(**result)
