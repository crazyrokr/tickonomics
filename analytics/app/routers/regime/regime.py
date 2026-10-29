"""Regime detection router: GARCH regime, CNN-LSTM hybrid, QED, RAHF."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.regime.regime_service import (
    cnn_lstm_regime,
    garch_regime,
    qed_regime,
    rahf_regime,
)

router = APIRouter()


class GarchRegimeRequest(BaseModel):
    returns: list[float]
    p: int = 1
    q: int = 1


class CnnLstmRegimeRequest(BaseModel):
    returns: list[float]
    lookback: int = 60


class QedRegimeRequest(BaseModel):
    capital_flow_proxies: dict[str, list[float]]
    current_state: dict[str, float]


class RahfRegimeRequest(BaseModel):
    returns: list[float]
    n_harmonics: int = 5


@router.post("/garch-regime")
def run_garch_regime(req: GarchRegimeRequest):
    return garch_regime(req.returns, req.p, req.q)


@router.post("/hybrid")
def run_cnn_lstm_regime(req: CnnLstmRegimeRequest):
    return cnn_lstm_regime(req.returns, req.lookback)


@router.post("/qed")
def run_qed_regime(req: QedRegimeRequest):
    return qed_regime(req.capital_flow_proxies, req.current_state)


@router.post("/rahf")
def run_rahf_regime(req: RahfRegimeRequest):
    return rahf_regime(req.returns, req.n_harmonics)
