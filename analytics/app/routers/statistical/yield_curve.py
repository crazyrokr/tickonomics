from fastapi import APIRouter

from app.models.requests import YieldCurveFitRequest, YieldCurveInterpolateRequest
from app.models.responses import YieldCurveFitResponse, YieldCurveInterpolateResponse
from app.services.statistical.yield_curve_service import (
    fit_yield_curve,
    interpolate_yield_curve,
)

router = APIRouter()


@router.post("/fit", response_model=YieldCurveFitResponse)
async def fit(request: YieldCurveFitRequest) -> YieldCurveFitResponse:
    result = fit_yield_curve(request.maturities, request.yields)
    return YieldCurveFitResponse(**result)


@router.post("/interpolate", response_model=YieldCurveInterpolateResponse)
async def interpolate(request: YieldCurveInterpolateRequest) -> YieldCurveInterpolateResponse:
    yields = interpolate_yield_curve(request.betas, request.tau, request.maturities)
    return YieldCurveInterpolateResponse(maturities=request.maturities, yields=yields)
