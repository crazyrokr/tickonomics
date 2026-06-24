"""Train the CNN-LSTM regime classifier and write its state_dict as a committed JSON artifact.

Reproducible: fixed seeds and a synthetic multi-regime returns series. Labels are derived from
rolling-volatility percentiles (the same boundary convention as ``garch_regime``), so the model learns a
deterministic target rather than running on random/untrained weights. Re-run after any architecture change
to keep ``app/services/regime/regime_cnn_lstm_state.json`` in sync. See ADR-036 D2.

Usage (from the analytics/ directory)::

    python scripts/train_regime_cnn_lstm.py
"""

import json
import sys
from pathlib import Path

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.services.regime._cnn_lstm import (  # noqa: E402  (import after sys.path bootstrap)
    HIDDEN_SIZE,
    INPUT_SIZE,
    NUM_CLASSES,
    _RegimeCNNLSTM,
)

ARTIFACT_PATH = (
    Path(__file__).resolve().parent.parent / "app" / "services" / "regime" / "regime_cnn_lstm_state.json"
)
LOOKBACK = 60
ROLLING_VOL_WINDOW = 21
SEED = 2024


def _synthetic_returns(seed: int = SEED, n_per_regime: int = 800) -> np.ndarray:
    rng = np.random.default_rng(seed)
    calm = rng.standard_normal(n_per_regime) * 0.005
    normal = rng.standard_normal(n_per_regime) * 0.02
    turbulent = rng.standard_normal(n_per_regime) * 0.06
    return np.concatenate([calm, normal, turbulent])


def _rolling_volatility(returns: np.ndarray, window: int = ROLLING_VOL_WINDOW) -> np.ndarray:
    vol = np.full(len(returns), np.nan)
    for i in range(window - 1, len(returns)):
        vol[i] = float(np.std(returns[i - window + 1 : i + 1], ddof=1))
    return vol


def _label_windows(returns: np.ndarray, lookback: int) -> tuple[np.ndarray, np.ndarray]:
    rolling_vol = _rolling_volatility(returns)
    valid = rolling_vol[~np.isnan(rolling_vol)]
    low_threshold = float(np.percentile(valid, 25))
    high_threshold = float(np.percentile(valid, 90))

    features: list[np.ndarray] = []
    labels: list[int] = []
    for i in range(len(returns) - lookback):
        window_vol = rolling_vol[i + lookback - 1]
        if np.isnan(window_vol):
            continue
        if window_vol < low_threshold:
            label = 0
        elif window_vol > high_threshold:
            label = 2
        else:
            label = 1
        features.append(returns[i : i + lookback])
        labels.append(label)

    x = np.asarray(features, dtype=np.float32).reshape(-1, lookback, INPUT_SIZE)
    y = np.asarray(labels, dtype=np.int64)
    return x, y


def train(epochs: int = 80, learning_rate: float = 1e-3, batch_size: int = 64) -> dict:
    torch.manual_seed(SEED)
    np.random.seed(SEED)

    returns = _synthetic_returns()
    x, y = _label_windows(returns, LOOKBACK)

    model = _RegimeCNNLSTM(INPUT_SIZE, HIDDEN_SIZE, NUM_CLASSES)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    criterion = torch.nn.CrossEntropyLoss()

    x_tensor = torch.tensor(x)
    y_tensor = torch.tensor(y)
    n_samples = len(x_tensor)
    last_loss = float("nan")
    for _ in range(epochs):
        permutation = torch.randperm(n_samples)
        for start in range(0, n_samples, batch_size):
            idx = permutation[start : start + batch_size]
            logits = model(x_tensor[idx])
            loss = criterion(logits, y_tensor[idx])
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
        last_loss = loss.item()

    model.eval()
    with torch.no_grad():
        predictions = model(x_tensor).argmax(dim=-1)
        accuracy = float((predictions == y_tensor).float().mean())

    state = {key: value.detach().cpu().tolist() for key, value in model.state_dict().items()}
    return {"state": state, "accuracy": accuracy, "last_loss": last_loss, "n_samples": n_samples}


def main() -> None:
    result = train()
    ARTIFACT_PATH.write_text(json.dumps(result["state"], separators=(",", ":")), encoding="utf-8")
    print(
        f"wrote {ARTIFACT_PATH}\n"
        f"accuracy={result['accuracy']:.4f} last_loss={result['last_loss']:.6f} "
        f"n_samples={result['n_samples']}"
    )


if __name__ == "__main__":
    main()
