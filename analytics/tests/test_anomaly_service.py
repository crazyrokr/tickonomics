import numpy as np
import pytest

from app.services.anomaly.anomaly_service import detect_anomalies, train_autoencoder


def test_train_autoencoder_produces_model_state():
    """Given 200 normal samples with 10 features, when training, then status is TRAINED and model_state is non-empty."""
    rng = np.random.default_rng(42)
    data = rng.standard_normal((200, 10)).tolist()

    result = train_autoencoder(data, encoding_dim=3, epochs=20)

    assert "error" not in result
    assert result["status"] == "TRAINED"
    assert len(result["model_state"]) > 0
    assert result["input_dim"] == 10
    assert result["encoding_dim"] == 3
    assert result["threshold"] > 0
    assert result["mean_loss"] >= 0


def test_train_insufficient_data_returns_error():
    """Given fewer than 20 samples, when training, then error returned."""
    data = [[1.0, 2.0, 3.0]] * 5

    result = train_autoencoder(data, encoding_dim=2)

    assert "error" in result
    assert "20" in result["error"]


def test_train_encoding_dim_too_large_returns_error():
    """Given encoding_dim >= input_dim, when training, then error returned."""
    data = [[1.0, 2.0]] * 50

    result = train_autoencoder(data, encoding_dim=3)

    assert "error" in result


def test_detect_anomalies_flags_outliers():
    """Given trained model with injected outliers, when detecting, then anomalies are flagged."""
    rng = np.random.default_rng(42)
    normal_data = rng.standard_normal((200, 5)).tolist()

    train_result = train_autoencoder(normal_data, encoding_dim=2, epochs=30)
    assert "error" not in train_result

    test_data = rng.standard_normal((20, 5)).tolist()
    test_data[0] = [v * 10.0 for v in test_data[0]]
    test_data[5] = [v * 8.0 for v in test_data[5]]

    result = detect_anomalies(
        test_data,
        train_result["model_state"],
        threshold=train_result["threshold"],
    )

    assert "error" not in result
    assert result["n_anomalies"] > 0
    assert len(result["anomaly_mask"]) == 20
    assert len(result["reconstruction_errors"]) == 20
    assert result["n_samples"] == 20


def test_detect_all_normal_returns_no_anomalies():
    """Given trained model and normal data, when detecting with high threshold, then no anomalies flagged."""
    rng = np.random.default_rng(7)
    data = rng.standard_normal((200, 4)).tolist()

    train_result = train_autoencoder(data, encoding_dim=2, epochs=20)
    assert "error" not in train_result

    test_data = rng.standard_normal((10, 4)).tolist()
    high_threshold = train_result["threshold"] * 10.0

    result = detect_anomalies(test_data, train_result["model_state"], threshold=high_threshold)

    assert "error" not in result
    assert result["n_anomalies"] == 0


def test_detect_with_invalid_model_state_returns_error():
    """Given garbage model state, when detecting, then error returned."""
    data = [[1.0, 2.0, 3.0]] * 5

    result = detect_anomalies(data, "not-valid-base64!!")

    assert "error" in result


def test_detect_empty_data_returns_error():
    """Given empty data, when detecting, then error returned."""
    result = detect_anomalies([], "some-state")

    assert "error" in result


def test_detect_wrong_feature_count_returns_error():
    """Given data with wrong number of features, when detecting, then error returned."""
    rng = np.random.default_rng(42)
    data = rng.standard_normal((100, 5)).tolist()
    train_result = train_autoencoder(data, encoding_dim=2, epochs=10)

    wrong_data = rng.standard_normal((10, 3)).tolist()

    result = detect_anomalies(wrong_data, train_result["model_state"])

    assert "error" in result
    assert "3" in result["error"]
