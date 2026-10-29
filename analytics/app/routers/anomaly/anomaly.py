"""Anomaly detection router: train autoencoder, detect anomalies."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.anomaly.anomaly_service import detect_anomalies, train_autoencoder

router = APIRouter()


class TrainAnomalyRequest(BaseModel):
    data: list[list[float]]
    encoding_dim: int = 3
    epochs: int = 100
    learning_rate: float = 0.001


class DetectAnomalyRequest(BaseModel):
    data: list[list[float]]
    model_state: str
    threshold: float | None = None
    threshold_multiplier: float = 3.0


@router.post("/train")
def run_train(req: TrainAnomalyRequest):
    return train_autoencoder(req.data, req.encoding_dim, req.epochs, req.learning_rate)


@router.post("/detect")
def run_detect(req: DetectAnomalyRequest):
    return detect_anomalies(req.data, req.model_state, req.threshold, req.threshold_multiplier)
