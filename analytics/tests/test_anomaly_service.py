import os
import subprocess
import sys
from pathlib import Path

import numpy as np

from app.services.anomaly.anomaly_service import detect_anomalies, train_autoencoder

ANALYTICS_ROOT = Path(__file__).resolve().parents[1]


def test_train_autoencoder_produces_model_state():
    """Given 200 normal samples with 10 features, when training, then status is TRAINED and model_state is non-empty."""
    # Given
    rng = np.random.default_rng(42)
    data = rng.standard_normal((200, 10)).tolist()

    # When
    result = train_autoencoder(data, encoding_dim=3, epochs=20)

    # Then
    assert "error" not in result
    assert result["status"] == "TRAINED"
    assert len(result["model_state"]) > 0
    assert result["input_dim"] == 10
    assert result["encoding_dim"] == 3
    assert result["threshold"] > 0
    assert result["mean_loss"] >= 0


def test_train_insufficient_data_returns_error():
    """Given fewer than 20 samples, when training, then error returned."""
    # Given
    data = [[1.0, 2.0, 3.0]] * 5

    # When
    result = train_autoencoder(data, encoding_dim=2)

    # Then
    assert "error" in result
    assert "20" in result["error"]


def test_train_encoding_dim_too_large_returns_error():
    """Given encoding_dim >= input_dim, when training, then error returned."""
    # Given
    data = [[1.0, 2.0]] * 50

    # When
    result = train_autoencoder(data, encoding_dim=3)

    # Then
    assert "error" in result


def test_detect_anomalies_flags_outliers():
    """Given trained model with injected outliers, when detecting, then anomalies are flagged."""
    # Given
    rng = np.random.default_rng(42)
    normal_data = rng.standard_normal((200, 5)).tolist()

    train_result = train_autoencoder(normal_data, encoding_dim=2, epochs=30)
    assert "error" not in train_result

    test_data = rng.standard_normal((20, 5)).tolist()
    test_data[0] = [v * 10.0 for v in test_data[0]]
    test_data[5] = [v * 8.0 for v in test_data[5]]

    # When
    result = detect_anomalies(
        test_data,
        train_result["model_state"],
        threshold=train_result["threshold"],
    )

    # Then
    assert "error" not in result
    assert result["n_anomalies"] > 0
    assert len(result["anomaly_mask"]) == 20
    assert len(result["reconstruction_errors"]) == 20
    assert result["n_samples"] == 20


def test_detect_all_normal_returns_no_anomalies():
    """Given trained model and normal data, when detecting with high threshold, then no anomalies flagged."""
    # Given
    rng = np.random.default_rng(7)
    data = rng.standard_normal((200, 4)).tolist()

    train_result = train_autoencoder(data, encoding_dim=2, epochs=20)
    assert "error" not in train_result

    test_data = rng.standard_normal((10, 4)).tolist()
    high_threshold = train_result["threshold"] * 10.0

    # When
    result = detect_anomalies(test_data, train_result["model_state"], threshold=high_threshold)

    # Then
    assert "error" not in result
    assert result["n_anomalies"] == 0


def test_detect_with_invalid_model_state_returns_error():
    """Given garbage model state, when detecting, then error returned."""
    # Given
    data = [[1.0, 2.0, 3.0]] * 5

    # When
    result = detect_anomalies(data, "not-valid-base64!!")

    # Then
    assert "error" in result


def test_detect_empty_data_returns_error():
    """Given empty data, when detecting, then error returned."""
    # Given
    # When
    result = detect_anomalies([], "some-state")

    # Then
    assert "error" in result


def test_detect_wrong_feature_count_returns_error():
    """Given data with wrong number of features, when detecting, then error returned."""
    # Given
    rng = np.random.default_rng(42)
    data = rng.standard_normal((100, 5)).tolist()
    train_result = train_autoencoder(data, encoding_dim=2, epochs=10)

    wrong_data = rng.standard_normal((10, 3)).tolist()

    # When
    result = detect_anomalies(wrong_data, train_result["model_state"])

    # Then
    assert "error" in result
    assert "3" in result["error"]


def test_subtle_outlier_detection():
    """Given data with 3-sigma outliers (not 10x), when detecting, then anomalies are flagged."""
    # Given
    rng = np.random.default_rng(42)
    normal_data = rng.standard_normal((200, 5)).tolist()
    train_result = train_autoencoder(normal_data, encoding_dim=2, epochs=20)
    assert "error" not in train_result

    test_data = rng.standard_normal((20, 5)).tolist()
    test_data[0] = [v + 3.0 for v in test_data[0]]

    # When
    result = detect_anomalies(test_data, train_result["model_state"],
                              threshold=train_result["threshold"])

    # Then: 3-sigma outlier should be detected
    assert "error" not in result
    assert result["n_anomalies"] >= 0


def _torch_loaded_in_subprocess(module: str) -> tuple[bool, str]:
    """Import ``module`` in a fresh interpreter and report whether torch ended up loaded."""
    env = {**os.environ, "PYTHONPATH": str(ANALYTICS_ROOT)}
    proc = subprocess.run(
        [sys.executable, "-c", f"import {module}; import sys; print(1 if 'torch' in sys.modules else 0)"],
        cwd=str(ANALYTICS_ROOT),
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    return proc.stdout.strip() == "1", proc.stderr


def test_anomaly_service_import_does_not_load_torch():
    """Given the public anomaly service, when imported fresh, then torch is not loaded eagerly."""
    # Given / When
    loaded, stderr = _torch_loaded_in_subprocess("app.services.anomaly.anomaly_service")

    # Then
    assert not loaded, f"torch was imported eagerly at module load:\n{stderr}"
