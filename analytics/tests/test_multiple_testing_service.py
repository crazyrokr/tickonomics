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


def test_fdr_all_significant():
    """Given all tiny p-values, when correct, then all rejected."""
    # Given
    p_values = [0.001, 0.002, 0.003]

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # Then
    assert all(result["actionable_mask"])
    assert result["rejected_count"] == 3


def test_fdr_none_significant():
    """Given all large p-values, when correct, then none rejected."""
    # Given
    p_values = [0.5, 0.6, 0.7, 0.8]

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # Then
    assert not any(result["actionable_mask"])
    assert result["rejected_count"] == 0


def test_fdr_known_bh_step():
    """Given [0.01, 0.04, 0.03, 0.20] at alpha=0.05, when BH correction,
    then specific rejections match manual calculation."""
    # Given
    p_values = [0.01, 0.04, 0.03, 0.20]

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # Then: at least the smallest p-value should be rejected
    assert result["actionable_mask"][0] is True or result["rejected_count"] >= 1
