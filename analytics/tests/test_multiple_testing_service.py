import numpy as np

from app.services.statistical.multiple_testing_service import apply_fdr_correction


def test_fdr_rejects_false_positives():
    """Given 100 uniform random p-values, when correct with alpha=0.05,
    then approximately 0 true bits."""
    # Given
    rng = np.random.default_rng(42)
    p_values = rng.uniform(0, 1, size=100).tolist()

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # Then
    assert result["rejected_count"] <= 10
    assert len(result["actionable_mask"]) == 100
    assert len(result["adjusted_p_values"]) == 100


def test_fdr_detects_true_positives():
    """Given 100 p-values where 5 are 0.001, when correct,
    then approximately 5 true bits."""
    # Given
    p_values = [0.001] * 5 + [0.5] * 95

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # Then
    assert result["rejected_count"] >= 4
    assert result["discovery_reduction"] >= 0.0


def test_empty_p_values():
    """Given empty list, when correct, then returns empty mask."""
    # Given
    p_values = []

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # Then
    assert result["actionable_mask"] == []
    assert result["adjusted_p_values"] == []
    assert result["rejected_count"] == 0
    assert result["discovery_reduction"] == 0.0
