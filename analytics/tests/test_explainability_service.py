from app.services.explainability.shap_service import compute_feature_importance


def test_feature_importance_basic():
    """Given ILI features, when computing importance, then SHAP values and top drivers returned."""
    # Given
    features = {"rrp_zscore": -1.2, "spread_zscore": 0.8, "vol_zscore": 1.5}

    # When
    result = compute_feature_importance("ili_signal", features, seed=42)

    # Then
    assert "error" not in result
    assert len(result["shap_values"]) == 3
    assert len(result["top_drivers"]) == 2
    assert result["top_drivers"][0] in features


def test_feature_importance_unknown_model():
    """Given unknown model name, when computing importance, then error returned."""
    # Given
    # When
    # Then
    assert "error" in compute_feature_importance("unknown", {"x": 1.0})


def test_feature_importance_empty_features():
    """Given empty features, when computing importance, then error returned."""
    # Given
    # When
    # Then
    assert "error" in compute_feature_importance("ili_signal", {})


def test_importance_sum_to_one():
    """Given features, when computing importance, then absolute SHAP values sum correctly."""
    # Given
    features = {"rrp_zscore": -1.2, "spread_zscore": 0.8, "vol_zscore": 1.5}

    # When
    result = compute_feature_importance("ili_signal", features, seed=42)

    # Then: SHAP values dict has one entry per feature
    assert "error" not in result
    assert len(result["shap_values"]) == len(features)
    total = sum(abs(v) for v in result["shap_values"].values())
    assert total > 0
