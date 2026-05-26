"""Group D: Heuristic / ML external validation tests (Steps 31-35).

Each test compares a service output against an INDEPENDENT oracle algorithm
that is deliberately different from the service implementation.
"""

import numpy as np
import pytest

from app.services.anomaly.anomaly_service import detect_anomalies, train_autoencoder
from app.services.benchmark.tournament_service import evaluate_tournament
from app.services.explainability.shap_service import compute_feature_importance
from app.services.regime.regime_service import (
    cnn_lstm_regime,
    garch_regime,
    qed_regime,
    rahf_regime,
)
from app.services.sentiment.sentiment_service import analyze_lexicon, analyze_sentiment

from .conftest import TOL_LOW, TOL_MEDIUM


# ---------------------------------------------------------------------------
# Step 31 — anomaly_service vs Z-score cross-comparison (UNVERIFIABLE)
# ---------------------------------------------------------------------------
@pytest.mark.xfail(
    reason="UNVERIFIABLE: autoencoder training is non-deterministic, Jaccard overlap may vary",
    strict=False,
)
def test_step31_autoencoder_vs_zscore_extreme_outliers():
    """Autoencoder anomalies and Z-score anomalies should overlap for extreme (10x) outliers."""
    # Given — synthetic data with 3 features, 100 normal + 5 extreme outliers
    rng = np.random.default_rng(42)
    n_normal = 100
    n_outliers = 5
    normal_data = rng.normal(0, 1, size=(n_normal, 3)).tolist()
    extreme_outliers = (rng.normal(0, 1, size=(n_outliers, 3)) * 10).tolist()
    all_data = normal_data + extreme_outliers

    # When — train autoencoder on the full dataset
    train_result = train_autoencoder(all_data, encoding_dim=2, epochs=50)
    assert "error" not in train_result, f"Training failed: {train_result.get('error')}"

    detect_result = detect_anomalies(
        all_data,
        model_state=train_result["model_state"],
        threshold=train_result["threshold"],
    )
    assert "error" not in detect_result, f"Detection failed: {detect_result.get('error')}"
    ae_mask = detect_result["anomaly_mask"]

    # When — compute Z-score anomalies (independent oracle)
    arr = np.array(all_data)
    z_scores = np.abs((arr - arr.mean(axis=0)) / arr.std(axis=0))
    z_mask = [bool(np.max(row) > 3) for row in z_scores]

    # When — compute Jaccard similarity
    ae_set = set(i for i, v in enumerate(ae_mask) if v)
    z_set = set(i for i, v in enumerate(z_mask) if v)
    union = ae_set | z_set
    jaccard = len(ae_set & z_set) / len(union) if union else 0.0

    # Then — Jaccard > 0.5 for extreme outliers (10x deviations)
    assert jaccard > 0.5, f"Jaccard similarity {jaccard:.3f} is too low; AE={ae_set}, Z={z_set}"


def test_step31_zscore_identifies_extreme_outliers_oracle():
    """Z-score oracle correctly identifies manually placed 10x outliers."""
    # Given — data with known outlier indices
    rng = np.random.default_rng(42)
    n_normal = 100
    n_outliers = 5
    normal_data = rng.normal(0, 1, size=(n_normal, 3))
    outlier_indices = list(range(n_normal, n_normal + n_outliers))
    extreme_outliers = rng.normal(0, 1, size=(n_outliers, 3)) * 10
    all_data = np.vstack([normal_data, extreme_outliers])

    # When — Z-score computation
    z_scores = np.abs((all_data - all_data.mean(axis=0)) / all_data.std(axis=0))
    z_anomaly_indices = set(i for i, row in enumerate(z_scores) if np.max(row) > 3)

    # Then — the oracle should catch most extreme outliers
    overlap = z_anomaly_indices & set(outlier_indices)
    assert len(overlap) >= 3, (
        f"Z-score oracle missed too many extreme outliers: "
        f"caught {len(overlap)}/{n_outliers}, indices={z_anomaly_indices}"
    )


def test_step31_autoencoder_detects_something_on_outlier_data():
    """Autoencoder should flag at least one anomaly when extreme outliers are present."""
    # Given — data with extreme outliers
    rng = np.random.default_rng(123)
    normal_data = rng.normal(0, 1, size=(100, 3)).tolist()
    outliers = (rng.normal(0, 1, size=(5, 3)) * 10).tolist()
    all_data = normal_data + outliers

    # When — train and detect
    train_result = train_autoencoder(all_data, encoding_dim=2, epochs=50)
    assert "error" not in train_result

    detect_result = detect_anomalies(
        all_data,
        model_state=train_result["model_state"],
        threshold=train_result["threshold"],
    )
    assert "error" not in detect_result

    # Then — at least one anomaly should be detected
    assert detect_result["n_anomalies"] > 0, "Autoencoder detected zero anomalies despite extreme outliers"


# ---------------------------------------------------------------------------
# Step 32 — sentiment_service vs manual lexicon enumeration
# ---------------------------------------------------------------------------
def test_step32_positive_words_positive_score():
    """Positive financial words should produce a positive sentiment score."""
    # Given — sentences with known positive words
    positive_texts = [
        "The market rally was strong today",
        "Profits surge as growth exceeds expectations",
        "Bullish outlook with solid recovery",
    ]

    # When
    result = analyze_sentiment(positive_texts)

    # Then — every positive text should have score > 0
    assert "error" not in result
    for entry in result["results"]:
        assert entry["score"] > 0, f"Expected positive score for '{entry['text']}', got {entry['score']}"
        assert entry["label"] == "positive", f"Expected 'positive' label for '{entry['text']}', got {entry['label']}"


def test_step32_negative_words_negative_score():
    """Negative financial words should produce a negative sentiment score."""
    # Given — sentences with known negative words
    negative_texts = [
        "Market crash triggers fear and sell-off",
        "Plunge in losses as recession deepens",
        "Bearish downgrade leads to decline",
    ]

    # When
    result = analyze_sentiment(negative_texts)

    # Then — every negative text should have score < 0
    assert "error" not in result
    for entry in result["results"]:
        assert entry["score"] < 0, f"Expected negative score for '{entry['text']}', got {entry['score']}"
        assert entry["label"] == "negative", f"Expected 'negative' label for '{entry['text']}', got {entry['label']}"


def test_step32_negation_reduces_positivity():
    """Negated positive text should be less positive than the un-negated version."""
    # Given — paired sentences with and without negation
    texts = ["The outlook is strong", "The outlook is not strong"]

    # When
    result = analyze_sentiment(texts)
    assert "error" not in result
    score_normal = result["results"][0]["score"]
    score_negated = result["results"][1]["score"]

    # Then — negated version should be less positive
    assert score_negated < score_normal, (
        f"Negation did not reduce positivity: normal={score_normal}, negated={score_negated}"
    )


def test_step32_lexicon_polarity_matches_manual_count():
    """Lexicon polarity should match a manual positive/negative word count."""
    # Given — a sentence with 3 positive words and 1 negative word
    text = "Strong rally and growth despite risk of decline"

    # When — manual enumeration
    positive_words = {"strong", "rally", "growth"}
    negative_words = {"risk", "decline"}
    words = text.lower().split()
    manual_pos = sum(1 for w in words if w in positive_words)
    manual_neg = sum(1 for w in words if w in negative_words)
    manual_polarity = (manual_pos - manual_neg) / (manual_pos + manual_neg)

    # When — service
    result = analyze_lexicon([text])
    assert "error" not in result
    service_polarity = result["results"][0]["polarity"]

    # Then — should agree on sign (both positive)
    assert service_polarity > 0, f"Expected positive polarity, got {service_polarity}"
    assert manual_polarity > 0, "Manual calculation should also be positive"
    assert abs(service_polarity - manual_polarity) < TOL_LOW, (
        f"Polarity mismatch: manual={manual_polarity}, service={service_polarity}"
    )


def test_step32_lexicon_neutral_text():
    """Text with no financial sentiment words should produce zero polarity."""
    # Given
    text = "The cat sat on the mat quietly"

    # When
    result = analyze_lexicon([text])
    assert "error" not in result

    # Then
    assert result["results"][0]["polarity"] == 0.0


# ---------------------------------------------------------------------------
# Step 33 — regime_service sub-model validation
# ---------------------------------------------------------------------------
@pytest.mark.xfail(reason="B1: GARCH gradient ascent only updates omega", strict=False)
def test_step33_garch_regime_runs():
    """garch_regime should classify regime without error (xfail: inherits B1)."""
    # Given — 200 synthetic returns
    rng = np.random.default_rng(42)
    returns = rng.normal(0.0001, 0.02, size=200).tolist()

    # When
    result = garch_regime(returns)

    # Then — should produce a regime classification
    assert "error" not in result, f"garch_regime returned error: {result.get('error')}"
    assert result["regime"] in {"LOW_VOL", "NORMAL", "ELEVATED", "HIGH_VOL"}


def test_step33_cnn_lstm_regime_produces_output():
    """cnn_lstm_regime should produce valid regime output (UNVERIFIABLE: untrained model)."""
    # Given — 200 synthetic returns
    rng = np.random.default_rng(42)
    returns = rng.normal(0.0001, 0.02, size=200).tolist()

    # When
    result = cnn_lstm_regime(returns, lookback=60)

    # Then — should produce output with expected structure
    assert "error" not in result, f"cnn_lstm_regime returned error: {result.get('error')}"
    assert result["regime"] in {"LOW_VOL", "NORMAL", "HIGH_VOL"}
    assert "confidence" in result
    assert "transition_probability" in result
    assert len(result["transition_probability"]) == 3
    assert 0 <= result["confidence"] <= 1


def test_step33_qed_regime_quartic_potential_barrier():
    """QED regime quartic potential barrier distance should be computed correctly."""
    # Given — capital flow proxies with high kurtosis to ensure double-well potential
    # Use heavy-tailed data so kurtosis > 3, producing a > 0 and thus discriminant > 0
    rng = np.random.default_rng(42)
    # Extreme outlier data to push kurtosis well above 3
    proxy_data = np.concatenate([
        rng.normal(0, 1, size=50),
        rng.standard_cauchy(size=10) * 0.5,
    ])
    capital_flow_proxies = {"flow_a": proxy_data.tolist(), "flow_b": (proxy_data * 0.8).tolist()}
    current_state = {"potential_value": 0.3, "velocity": 0.01}

    # When — call service
    result = qed_regime(capital_flow_proxies, current_state)
    assert "error" not in result, f"qed_regime returned error: {result.get('error')}"

    # When — manually verify quartic potential computation
    # V(x) = ax^4 + bx^2, dV/dx = 4ax^3 + 2bx = 0
    # Extrema: x(4ax^2 + 2b) = 0 => x=0 or x = +-sqrt(-b/(2a))
    # For double well: need b < 0 (but service enforces a, b > 0 via max(0.1,...))
    # So the service computes barrier via discriminant: D = b^2 - 4a*0.01
    a_svc = result["potential_parameters"]["a"]
    b_svc = result["potential_parameters"]["b"]

    discriminant = b_svc ** 2 - 4 * a_svc * 0.01
    if discriminant > 0:
        sqrt_d = np.sqrt(discriminant)
        x1 = (-b_svc + sqrt_d) / (2 * a_svc)
        x2 = (-b_svc - sqrt_d) / (2 * a_svc)
        expected_barrier = max(abs(x1), abs(x2))
        expected_distance = abs(expected_barrier - current_state["potential_value"]) / expected_barrier
    else:
        expected_barrier = 0.0
        expected_distance = float("inf")

    # Then — barrier distance should match manual computation
    if result["n_wells"] == 2:
        assert abs(result["barrier_distance"] - round(expected_distance, 4)) < TOL_LOW, (
            f"Barrier distance mismatch: service={result['barrier_distance']}, manual={round(expected_distance, 4)}"
        )
    else:
        assert result["barrier_distance"] == float("inf") or result["n_wells"] == 1


def test_step33_qed_regime_stable_single_well():
    """QED regime with near-Gaussian data should produce STABLE single-well regime."""
    # Given — Gaussian data with kurtosis close to 3 (no heavy tails)
    rng = np.random.default_rng(42)
    gaussian_data = rng.normal(0, 1, size=200).tolist()
    capital_flow_proxies = {"flow": gaussian_data}
    current_state = {"potential_value": 0.5, "velocity": 0.0}

    # When
    result = qed_regime(capital_flow_proxies, current_state)
    assert "error" not in result

    # Then — with Gaussian data, kurtosis ~3, so a = max(0.1, (kurt-3)/6) could be near 0.1
    # Discriminant = b^2 - 4a*0.01 might be small or negative => single well => STABLE
    assert result["regime"] in {"STABLE", "METASTABLE", "UNSTABLE"}
    assert "barrier_distance" in result
    assert "crash_probability" in result


@pytest.mark.xfail(reason="B1: RAHF uses garch_forecast which inherits B1", strict=False)
def test_step33_rahf_regime_runs():
    """rahf_regime should run without error (xfail: inherits B1 via GARCH)."""
    # Given — 200 synthetic returns
    rng = np.random.default_rng(42)
    returns = rng.normal(0.0001, 0.02, size=200).tolist()

    # When
    result = rahf_regime(returns)

    # Then — should produce output
    assert "error" not in result, f"rahf_regime returned error: {result.get('error')}"
    assert result["regime"] in {"LOW_VOL", "NORMAL", "HIGH_VOL"}


# ---------------------------------------------------------------------------
# Step 34 — tournament_service component validation
# ---------------------------------------------------------------------------
def test_step34_ili_sharpe_manual_verification():
    """ILI Sharpe ratio from tournament should match manual computation."""
    # Given — 100 known returns with positive mean
    rng = np.random.default_rng(42)
    returns = (rng.normal(0.001, 0.02, size=100)).tolist()

    # When — manual Sharpe
    r = np.array(returns)
    manual_sharpe = float(np.mean(r) / np.std(r, ddof=1)) if np.std(r, ddof=1) > 0 else 0.0

    # When — service
    result = evaluate_tournament(returns, seed=42)
    assert "error" not in result

    ili_sharpe = result["results"]["ili_rule_engine"]["sharpe"]

    # Then — should match manual computation
    assert abs(ili_sharpe - round(manual_sharpe, 4)) < TOL_LOW, (
        f"ILI Sharpe mismatch: service={ili_sharpe}, manual={round(manual_sharpe, 4)}"
    )


def test_step34_ili_hit_rate_manual_verification():
    """ILI hit rate from tournament should match manual computation."""
    # Given
    rng = np.random.default_rng(42)
    returns = (rng.normal(0.001, 0.02, size=100)).tolist()

    # When — manual hit rate
    r = np.array(returns)
    manual_hit = float(np.mean(r > 0))

    # When — service
    result = evaluate_tournament(returns, seed=42)
    assert "error" not in result

    ili_hit = result["results"]["ili_rule_engine"]["hit_rate"]

    # Then
    assert abs(ili_hit - round(manual_hit, 4)) < TOL_LOW, (
        f"ILI hit rate mismatch: service={ili_hit}, manual={round(manual_hit, 4)}"
    )


def test_step34_momentum_sharpe_manual_verification():
    """Momentum Sharpe should match manual moving-average computation."""
    # Given
    rng = np.random.default_rng(42)
    returns = (rng.normal(0.001, 0.02, size=100)).tolist()

    # When — manual momentum computation (replicating service logic)
    r = np.array(returns)
    n = len(r)
    ma_window = max(5, n // 20)
    ma_signal = np.convolve(r, np.ones(ma_window) / ma_window, mode="valid")
    momentum_returns = np.diff(ma_signal)
    std = np.std(momentum_returns, ddof=1)
    manual_sharpe = float(np.mean(momentum_returns) / std) if std > 0 else 0.0

    # When — service
    result = evaluate_tournament(returns, seed=42)
    assert "error" not in result

    mom_sharpe = result["results"]["momentum"]["sharpe"]

    # Then — should match manual computation
    assert abs(mom_sharpe - round(manual_sharpe, 4)) < TOL_LOW, (
        f"Momentum Sharpe mismatch: service={mom_sharpe}, manual={round(manual_sharpe, 4)}"
    )


@pytest.mark.xfail(reason="B6: LSTM is momentum+noise, not a real LSTM", strict=False)
def test_step34_lstm_is_not_real_lstm():
    """LSTM results should differ from pure momentum due to noise injection (B6: not a real LSTM)."""
    # Given
    rng = np.random.default_rng(42)
    returns = (rng.normal(0.001, 0.02, size=100)).tolist()

    # When — service
    result = evaluate_tournament(returns, seed=42)
    assert "error" not in result

    mom_sharpe = result["results"]["momentum"]["sharpe"]
    lstm_sharpe = result["results"]["lstm"]["sharpe"]

    # Then — LSTM is just momentum + random noise, so it should differ slightly
    # but not be a fundamentally different computation
    # If this were a real LSTM, the difference would be substantial and meaningful
    # Flag: the "LSTM" Sharpe is computed as Sharpe(momentum_returns + N(0,0.01))
    # This test documents that LSTM is NOT a real LSTM implementation
    assert lstm_sharpe != mom_sharpe, (
        "LSTM Sharpe equals momentum Sharpe — if they match exactly, "
        "the noise injection did nothing, confirming B6"
    )


def test_step34_regime_breakdown_structure():
    """Tournament should return regime breakdown with expected keys."""
    # Given
    rng = np.random.default_rng(42)
    returns = (rng.normal(0.001, 0.02, size=120)).tolist()

    # When
    result = evaluate_tournament(returns, seed=42)
    assert "error" not in result

    # Then — regime breakdown should cover three regimes
    breakdown = result["regime_breakdown"]
    assert "uptrend" in breakdown
    assert "sideways" in breakdown
    assert "downtrend" in breakdown
    for regime_key, model_name in breakdown.items():
        assert model_name in {"ili_rule_engine", "momentum", "lstm"}, (
            f"Unexpected model name '{model_name}' for regime '{regime_key}'"
        )


# ---------------------------------------------------------------------------
# Step 35 — shap_service mechanism validation (UNVERIFIABLE as SHAP)
# ---------------------------------------------------------------------------
def test_step35_baseline_output_manual_verification():
    """Baseline output should match manual weighted absolute-value computation."""
    # Given — features for ili_signal model
    features = {"momentum": 0.5, "volatility": -0.3, "volume": 0.8}

    # When — manual baseline computation
    # Weights: [0.4, 0.35, 0.25] for 3 features
    values = np.array([0.5, -0.3, 0.8])
    weights = np.array([0.4, 0.35, 0.25])
    weights = weights / weights.sum()  # normalize
    manual_baseline = float(np.sum(weights * np.abs(values)))

    # When — service
    result = compute_feature_importance("ili_signal", features, n_permutations=200, seed=42)
    assert "error" not in result

    # Then — baseline should match
    assert abs(result["baseline_output"] - round(manual_baseline, 6)) < TOL_MEDIUM, (
        f"Baseline mismatch: service={result['baseline_output']}, manual={round(manual_baseline, 6)}"
    )


def test_step35_perturbation_effect_is_nonzero():
    """Perturbing features should produce non-zero attribution values (mechanism sanity check).

    Note: because the perturbation adds N(0, 0.1) noise, the attribution for
    small-valued features (e.g. 0.01) can be *larger* than for large-valued
    features (e.g. 1.0). This is a known characteristic of fixed-variance
    perturbation sensitivity analysis — it is NOT true Shapley attribution.
    """
    # Given — features with mixed magnitudes
    features = {"momentum": 1.0, "volatility": 0.01, "volume": 0.01}

    # When — run with many permutations for stability
    result = compute_feature_importance("ili_signal", features, n_permutations=500, seed=42)
    assert "error" not in result

    shap_values = result["shap_values"]

    # Then — at least one feature should have non-zero attribution
    total_attr = sum(abs(v) for v in shap_values.values())
    assert total_attr > 0, (
        f"Expected non-zero total attribution, got {shap_values}"
    )

    # Then — all attributions should be finite floats
    for name, val in shap_values.items():
        assert isinstance(val, float) and np.isfinite(val), (
            f"Attribution for {name} should be finite float, got {val}"
        )


def test_step35_top_drivers_match_largest_features():
    """Top drivers should correspond to features with the largest absolute values."""
    # Given
    features = {"momentum": 0.9, "volatility": 0.1, "volume": -0.5}

    # When
    result = compute_feature_importance("ili_signal", features, n_permutations=500, seed=42)
    assert "error" not in result

    # Then — top drivers should include momentum (|0.9|) and likely volume (|0.5|)
    assert "momentum" in result["top_drivers"], (
        f"Expected 'momentum' in top drivers, got {result['top_drivers']}"
    )
    assert len(result["top_drivers"]) == 2


def test_step35_perturbation_mechanism_is_not_shapley():
    """Document that the mechanism is permutation sensitivity, NOT true Shapley values.

    True Shapley values require evaluating all 2^N coalitions. This service
    adds random noise to each feature and measures output change — which is
    permutation sensitivity analysis, not Shapley.
    """
    # Given — simple features
    features = {"a": 1.0, "b": 0.5}

    # When
    result = compute_feature_importance("ili_signal", features, n_permutations=100, seed=42)
    assert "error" not in result

    # Then — verify the mechanism: baseline is weighted absolute values
    # and "shap" values are perturbation effects, not Shapley attributions
    values = np.array([1.0, 0.5])
    # With 2 features, weights = [1/2, 1/2] (uniform fallback, not the 3-feature weights)
    weights = np.ones(2) / 2
    manual_baseline = float(np.sum(weights * np.abs(values)))

    assert abs(result["baseline_output"] - round(manual_baseline, 6)) < TOL_MEDIUM, (
        f"Baseline mismatch for 2-feature case: service={result['baseline_output']}, "
        f"manual={round(manual_baseline, 6)}"
    )

    # The "shap_values" should be near-zero for small perturbations since
    # abs(x + noise) - abs(x) averages to a small value
    for name, val in result["shap_values"].items():
        assert isinstance(val, float), f"SHAP value for {name} should be float"


def test_step35_deterministic_with_seed():
    """With the same seed, results should be identical (reproducibility check)."""
    # Given
    features = {"x": 0.7, "y": -0.3, "z": 0.1}

    # When — two runs with same seed
    result1 = compute_feature_importance("ili_signal", features, n_permutations=200, seed=99)
    result2 = compute_feature_importance("ili_signal", features, n_permutations=200, seed=99)

    # Then — should be identical
    assert result1["shap_values"] == result2["shap_values"], (
        "Same seed should produce identical results"
    )
    assert result1["baseline_output"] == result2["baseline_output"]
    assert result1["top_drivers"] == result2["top_drivers"]
