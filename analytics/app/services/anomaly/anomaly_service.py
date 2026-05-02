"""Anomaly detection via autoencoder reconstruction error."""

import base64
import io

import numpy as np
import torch
import torch.nn as nn


def train_autoencoder(
    data: list[list[float]],
    encoding_dim: int = 3,
    epochs: int = 100,
    learning_rate: float = 0.001,
) -> dict:
    if len(data) < 20:
        return {"error": "At least 20 samples required for training"}

    x = torch.tensor(data, dtype=torch.float32)
    input_dim = x.shape[1]

    if encoding_dim >= input_dim:
        return {"error": "encoding_dim must be less than input dimension"}

    model = _Autoencoder(input_dim, encoding_dim)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    criterion = nn.MSELoss()

    losses = []
    for _ in range(epochs):
        reconstructed = model(x)
        loss = criterion(reconstructed, x)
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
        losses.append(loss.item())

    with torch.no_grad():
        reconstructed = model(x)
        errors = torch.mean((reconstructed - x) ** 2, dim=1).numpy()

    threshold = float(np.mean(errors) + 2.0 * np.std(errors))

    buffer = io.BytesIO()
    torch.save(model.state_dict(), buffer)
    model_state = base64.b64encode(buffer.getvalue()).decode("utf-8")

    return {
        "status": "TRAINED",
        "threshold": round(threshold, 8),
        "mean_loss": round(float(np.mean(losses[-10:])), 8),
        "input_dim": input_dim,
        "encoding_dim": encoding_dim,
        "model_state": model_state,
    }


def detect_anomalies(
    data: list[list[float]],
    model_state: str,
    threshold: float | None = None,
    threshold_multiplier: float = 3.0,
) -> dict:
    if len(data) == 0:
        return {"error": "No data provided"}

    try:
        raw = base64.b64decode(model_state)
        state_dict = torch.load(io.BytesIO(raw), weights_only=True)
    except Exception:
        return {"error": "Invalid model state"}

    keys = list(state_dict.keys())
    if not keys or "encoder.0.weight" not in state_dict or "encoder.2.weight" not in state_dict:
        return {"error": "Corrupt model state: missing expected layers"}

    input_dim = state_dict["encoder.0.weight"].shape[1]
    encoding_dim = state_dict["encoder.2.weight"].shape[0]

    model = _Autoencoder(input_dim, encoding_dim)
    model.load_state_dict(state_dict)
    model.eval()

    x = torch.tensor(data, dtype=torch.float32)
    if x.shape[1] != input_dim:
        return {"error": f"Expected {input_dim} features, got {x.shape[1]}"}

    with torch.no_grad():
        reconstructed = model(x)
        errors = torch.mean((reconstructed - x) ** 2, dim=1).numpy()

    if threshold is None:
        threshold = float(np.mean(errors) + threshold_multiplier * np.std(errors))

    anomaly_mask = [bool(e > threshold) for e in errors]

    return {
        "anomaly_mask": anomaly_mask,
        "reconstruction_errors": [round(float(e), 8) for e in errors],
        "threshold": round(threshold, 8),
        "n_anomalies": sum(anomaly_mask),
        "n_samples": len(data),
    }


class _Autoencoder(torch.nn.Module):
    def __init__(self, input_dim: int, encoding_dim: int):
        super().__init__()
        self.encoder = torch.nn.Sequential(
            torch.nn.Linear(input_dim, encoding_dim * 2),
            torch.nn.ReLU(),
            torch.nn.Linear(encoding_dim * 2, encoding_dim),
        )
        self.decoder = torch.nn.Sequential(
            torch.nn.Linear(encoding_dim, encoding_dim * 2),
            torch.nn.ReLU(),
            torch.nn.Linear(encoding_dim * 2, input_dim),
        )

    def forward(self, x):
        return self.decoder(self.encoder(x))
