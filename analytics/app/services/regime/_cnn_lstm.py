"""CNN-LSTM hybrid regime classifier (torch import deferred to this private module).

Kept separate from ``regime_service`` so importing the public service does not pull in torch: torch loads
only when the CNN-LSTM path is actually invoked (ADR-036 D1). Inference loads the trained checkpoint
shipped at ``regime_cnn_lstm_state.json`` and never falls back to random weights (ADR-036 D2); a missing
or corrupt checkpoint surfaces a clear error instead of silently classifying with untrained weights.
"""

import json
import os
from pathlib import Path

import numpy as np
import torch

LABELS = ("LOW_VOL", "NORMAL", "HIGH_VOL")
INPUT_SIZE = 1
HIDDEN_SIZE = 16
NUM_CLASSES = len(LABELS)


class _CheckpointError(Exception):
    """Raised when the trained CNN-LSTM checkpoint cannot be loaded or applied."""


class _RegimeCNNLSTM(torch.nn.Module):
    def __init__(self, input_size: int, hidden_size: int, num_classes: int):
        super().__init__()
        self.conv = torch.nn.Conv1d(input_size, 16, kernel_size=3, padding=1)
        self.lstm = torch.nn.LSTM(16, hidden_size, batch_first=True)
        self.fc = torch.nn.Linear(hidden_size, num_classes)

    def forward(self, x):
        x = x.permute(0, 2, 1)
        x = torch.relu(self.conv(x))
        x = x.permute(0, 2, 1)
        _, (hn, _) = self.lstm(x)
        return self.fc(hn[-1])


def default_state_path() -> Path:
    override = os.environ.get("REGIME_CNN_LSTM_STATE_PATH")
    if override:
        return Path(override)
    return Path(__file__).parent / "regime_cnn_lstm_state.json"


def build_model() -> _RegimeCNNLSTM:
    return _RegimeCNNLSTM(INPUT_SIZE, HIDDEN_SIZE, NUM_CLASSES)


def load_state_dict(path: Path | None = None) -> dict[str, torch.Tensor]:
    target = path or default_state_path()
    if not target.exists():
        raise _CheckpointError(f"CNN-LSTM checkpoint not found at {target}")
    try:
        raw = json.loads(target.read_text(encoding="utf-8"))
        if not isinstance(raw, dict) or not raw:
            raise ValueError("checkpoint payload is empty or not a JSON object")
        return {key: torch.tensor(value, dtype=torch.float32) for key, value in raw.items()}
    except (json.JSONDecodeError, ValueError) as exc:
        raise _CheckpointError(f"CNN-LSTM checkpoint at {target} is corrupt: {exc}") from exc


def run_cnn_lstm_regime(returns: list[float], lookback: int, state_path: Path | None = None) -> dict:
    r = np.array(returns)
    windows = np.array([r[i : i + lookback] for i in range(len(r) - lookback)])

    try:
        model = build_model()
        model.load_state_dict(load_state_dict(state_path))
    except (_CheckpointError, RuntimeError) as exc:
        return {"error": str(exc)}
    model.eval()

    last_window = torch.tensor(windows[-1], dtype=torch.float32).unsqueeze(0).unsqueeze(-1)

    with torch.no_grad():
        logits = model(last_window)
        probs = torch.softmax(logits, dim=-1).numpy()[0]

    predicted_idx = int(np.argmax(probs))
    transition = {LABELS[i]: round(float(probs[i]), 4) for i in range(NUM_CLASSES)}

    return {
        "regime": LABELS[predicted_idx],
        "confidence": round(float(probs[predicted_idx]), 4),
        "transition_probability": transition,
        "lookback": lookback,
        "n_observations": len(returns),
    }
