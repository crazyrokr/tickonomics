"""Reproducibility Disclosure Score (RDS) computation."""

_RUBRIC = {
    "has_code": {
        0: "No code available",
        1: "Code partially available or undocumented",
        2: "Full code available and executable",
    },
    "code_versioned": {
        0: "No version control",
        1: "Versioned but no tagged releases",
        2: "Semantic versioning with tagged releases",
    },
    "dataset_available": {
        0: "No dataset available",
        1: "Partial dataset or synthetic substitute",
        2: "Full dataset publicly accessible",
    },
    "hyperparams_documented": {
        0: "No hyperparameter documentation",
        1: "Partial documentation (missing defaults or ranges)",
        2: "Complete documentation with defaults and ranges",
    },
    "results_reproducible": {
        0: "No evidence of reproducibility",
        1: "Partially reproducible (within tolerance)",
        2: "Fully reproducible with deterministic seeding",
    },
}

_DIMENSION_MAP = {
    "code_availability": ["has_code", "code_versioned"],
    "data_availability": ["dataset_available"],
    "reproducibility": ["hyperparams_documented", "results_reproducible"],
}


def compute_rds(
    model_name: str,
    has_code: bool = False,
    code_versioned: bool = False,
    dataset_available: bool = False,
    hyperparams_documented: bool = False,
    results_reproducible: bool = False,
) -> dict:
    if not model_name:
        return {"error": "model_name is required"}

    checks = {
        "has_code": has_code,
        "code_versioned": code_versioned,
        "dataset_available": dataset_available,
        "hyperparams_documented": hyperparams_documented,
        "results_reproducible": results_reproducible,
    }

    scores = {}
    for key, value in checks.items():
        scores[key] = 2 if value else 0

    breakdown = {}
    for dimension, keys in _DIMENSION_MAP.items():
        dim_total = 2 * len(keys)
        dim_score = sum(scores[k] for k in keys)
        normalized = int(round(dim_score / dim_total * 2))
        breakdown[dimension] = min(2, max(0, normalized))

    total_rds = sum(breakdown.values())

    return {
        "model_name": model_name,
        "rds_score": total_rds,
        "max_rds_score": 6,
        "breakdown": breakdown,
        "dimension_details": {
            dim: {
                "fields": keys,
                "raw_scores": {k: scores[k] for k in keys},
                "normalized_score": breakdown[dim],
            }
            for dim, keys in _DIMENSION_MAP.items()
        },
    }
