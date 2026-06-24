"""Anomaly detection via autoencoder reconstruction error.

The torch-backed training and detection live in ``_autoencoder`` (imported lazily inside each function)
so importing this module does not load torch; see ADR-036 D1. The wrappers here perform only the cheap,
numpy-free input validation before delegating.
"""


def train_autoencoder(
    data: list[list[float]],
    encoding_dim: int = 3,
    epochs: int = 100,
    learning_rate: float = 0.001,
) -> dict:
    if len(data) < 20:
        return {"error": "At least 20 samples required for training"}

    from app.services.anomaly._autoencoder import train as _train

    return _train(data, encoding_dim, epochs, learning_rate)


def detect_anomalies(
    data: list[list[float]],
    model_state: str,
    threshold: float | None = None,
    threshold_multiplier: float = 3.0,
) -> dict:
    if len(data) == 0:
        return {"error": "No data provided"}

    from app.services.anomaly._autoencoder import detect as _detect

    return _detect(data, model_state, threshold, threshold_multiplier)
