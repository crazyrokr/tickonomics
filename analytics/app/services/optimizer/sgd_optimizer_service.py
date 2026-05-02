"""Online optimizer: SGD-based weight delta computation for ILI components."""

import numpy as np


def compute_weight_delta(
    current_weights: dict[str, float],
    signal_performance: list[dict[str, float]],
    learning_rate: float = 0.01,
    lookback_days: int | None = None,
    max_delta: float = 0.05,
) -> dict:
    if not current_weights:
        return {"error": "Current weights must be non-empty"}

    if not signal_performance:
        return {"error": "Signal performance data required"}

    weight_sum = sum(current_weights.values())
    if abs(weight_sum - 1.0) > 0.01:
        return {"error": "Current weights must sum to 1.0"}

    components = list(current_weights.keys())

    if lookback_days is not None:
        signal_performance = signal_performance[-lookback_days:]

    performance_matrix = np.zeros((len(signal_performance), len(components)))
    for i, perf in enumerate(signal_performance):
        for j, comp in enumerate(components):
            performance_matrix[i, j] = perf.get(comp, 0.0)

    mean_perf = np.mean(performance_matrix, axis=0)
    total_perf = np.sum(mean_perf)

    if total_perf == 0:
        deltas = {comp: 0.0 for comp in components}
    else:
        target_weights = mean_perf / total_perf
        raw_delta = learning_rate * (target_weights - np.array([current_weights[c] for c in components]))
        clipped_delta = np.clip(raw_delta, -max_delta, max_delta)
        deltas = {comp: round(float(clipped_delta[i]), 6) for i, comp in enumerate(components)}

    updated = {}
    for comp in components:
        updated[comp] = round(float(current_weights[comp]) + deltas.get(comp, 0.0), 6)

    weight_values = np.array(list(updated.values()))
    projected = bool(np.any(weight_values < 0) or abs(np.sum(weight_values) - 1.0) > 1e-6)

    if projected:
        weight_values = np.maximum(weight_values, 0.0)
        weight_values = weight_values / np.sum(weight_values)
        updated = {comp: round(float(weight_values[i]), 6) for i, comp in enumerate(components)}
        deltas = {
            comp: round(float(weight_values[i]) - current_weights[comp], 6)
            for i, comp in enumerate(components)
        }

    return {
        "updated_weights": updated,
        "weight_deltas": deltas,
        "projected": projected,
        "learning_rate": learning_rate,
        "lookback_samples": len(signal_performance),
    }
