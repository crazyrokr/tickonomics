import signal

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import health
from app.routers.anomaly import anomaly
from app.routers.backtest import backtest
from app.routers.climate import climate
from app.routers.diagnostics import diagnostics
from app.routers.drift import drift
from app.routers.econometrics import econometrics
from app.routers.explainability import explainability
from app.routers.fixed_income import fixed_income
from app.routers.greeks import greeks
from app.routers.liquidity import liquidity
from app.routers.ml import volatility
from app.routers.optimizer import optimizer
from app.routers.performance import performance
from app.routers.perspex import perspex
from app.routers.regime import regime
from app.routers.reproducibility import reproducibility
from app.routers.risk import risk
from app.routers.sentiment import sentiment
from app.routers.simulation import simulation
from app.routers.stops import stops
from app.routers.tournament import tournament
from app.routers.statistical import (
    evt_risk,
    macro_shock,
    multiple_testing,
    quantile_regression,
    transfer_entropy,
    yield_curve,
)

shutdown_requested = False


def _handle_sigterm(signum, frame):
    global shutdown_requested
    shutdown_requested = True


signal.signal(signal.SIGTERM, _handle_sigterm)

app = FastAPI(title="Tickonomics Analytics", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(econometrics.router, prefix="/api/v1/econometrics", tags=["econometrics"])
app.include_router(fixed_income.router, prefix="/api/v1/fixed-income", tags=["fixed-income"])
app.include_router(performance.router, prefix="/api/v1/performance", tags=["performance"])
app.include_router(risk.router, prefix="/api/v1/risk", tags=["risk"])
app.include_router(evt_risk.router, prefix="/api/v1/statistical/evt", tags=["evt"])
app.include_router(multiple_testing.router, prefix="/api/v1/statistical/fdr", tags=["fdr"])
app.include_router(yield_curve.router, prefix="/api/v1/statistical/yield-curve", tags=["yield-curve"])
app.include_router(macro_shock.router, prefix="/api/v1/statistical/macro", tags=["macro"])
app.include_router(quantile_regression.router, prefix="/api/v1/statistical/qr", tags=["quantile-regression"])
app.include_router(transfer_entropy.router, prefix="/api/v1/statistical/entropy", tags=["transfer-entropy"])
app.include_router(anomaly.router, prefix="/api/v1/anomaly", tags=["anomaly"])
app.include_router(regime.router, prefix="/api/v1/regime", tags=["regime"])
app.include_router(climate.router, prefix="/api/v1/climate", tags=["climate"])
app.include_router(drift.router, prefix="/api/v1/drift", tags=["drift"])
app.include_router(optimizer.router, prefix="/api/v1/optimizer", tags=["optimizer"])
app.include_router(simulation.router, prefix="/api/v1/simulate", tags=["simulation"])
app.include_router(greeks.router, prefix="/api/v1/greeks", tags=["greeks"])
app.include_router(liquidity.router, prefix="/api/v1/liquidity", tags=["liquidity"])
app.include_router(backtest.router, prefix="/api/v1/backtest", tags=["backtest"])
app.include_router(reproducibility.router, prefix="/api/v1/reproducibility", tags=["reproducibility"])
app.include_router(sentiment.router, prefix="/api/v1/sentiment", tags=["sentiment"])
app.include_router(stops.router, prefix="/api/v1/stops", tags=["stops"])
app.include_router(diagnostics.router, prefix="/api/v1/diagnostics", tags=["diagnostics"])
app.include_router(volatility.router, prefix="/api/v1/analytics", tags=["volatility"])
app.include_router(tournament.router, prefix="/api/v1/tournament", tags=["tournament"])
app.include_router(explainability.router, prefix="/api/v1/explainability", tags=["explainability"])
app.include_router(perspex.router, prefix="/api/v1/perspex", tags=["perspex"])
