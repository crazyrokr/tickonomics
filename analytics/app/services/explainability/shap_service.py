"""Feature importance via SHAP-style attribution."""

import numpy as np


def compute_feature_importance(
    model: str,
    features: dict[str, float],
    n_permutations: int = 100,
    seed: int | None = None,
) -> dict:
    if not features:
        return {"error": "No features provided"}

    if model != "ili_signal":
        return {"error": f"Unknown model: {model}. Supported: 'ili_signal'"}

    rng = np.random.default_rng(seed)
    feature_names = list(features.keys())
    values = np.array([features[k] for k in feature_names])

    weights = np.array([0.4, 0.35, 0.25]) if len(feature_names) == 3 else np.ones(len(feature_names)) / len(feature_names)
    weights = weights[:len(feature_names)]
    weights = weights / np.sum(weights)

    baseline = float(np.sum(weights * np.abs(values)))

    shap_values = {}
    for i, name in enumerate(feature_names):
        permuted_effects = []
        for _ in range(n_permutations):
            perturbed = values.copy()
            perturbed[i] = values[i] + rng.standard_normal() * 0.1
            permuted_output = float(np.sum(weights * np.abs(perturbed)))
            permuted_effects.append(permuted_output - baseline)

        shap_values[name] = round(float(np.mean(permuted_effects)), 6)

    total_abs = sum(abs(v) for v in shap_values.values())
    if total_abs > 0:
        sorted_features = sorted(shap_values.items(), key=lambda x: abs(x[1]), reverse=True)
    else:
        sorted_features = list(shap_values.items())

    top_drivers = [name for name, _ in sorted_features[:2]]

    return {
        "shap_values": shap_values,
        "top_drivers": top_drivers,
        "baseline_output": round(baseline, 6),
        "n_permutations": n_permutations,
    }
