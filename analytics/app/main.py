from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import health
from app.routers.statistical import (
    evt_risk,
    macro_shock,
    multiple_testing,
    quantile_regression,
    transfer_entropy,
    yield_curve,
)

app = FastAPI(title="Tickonomics Analytics", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(evt_risk.router, prefix="/api/v1/statistical/evt", tags=["evt"])
app.include_router(multiple_testing.router, prefix="/api/v1/statistical/fdr", tags=["fdr"])
app.include_router(yield_curve.router, prefix="/api/v1/statistical/yield-curve", tags=["yield-curve"])
app.include_router(macro_shock.router, prefix="/api/v1/statistical/macro", tags=["macro"])
app.include_router(quantile_regression.router, prefix="/api/v1/statistical/qr", tags=["quantile-regression"])
app.include_router(transfer_entropy.router, prefix="/api/v1/statistical/entropy", tags=["transfer-entropy"])
