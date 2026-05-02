from app.services.reproducibility.rds_scorer_service import compute_rds


def test_rds_full_disclosure():
    """Given all flags true, when computing RDS, then score is max (6)."""
    result = compute_rds(
        model_name="test_model",
        has_code=True,
        code_versioned=True,
        dataset_available=True,
        hyperparams_documented=True,
        results_reproducible=True,
    )

    assert "error" not in result
    assert result["rds_score"] == 6
    assert result["model_name"] == "test_model"
    assert all(v == 2 for v in result["breakdown"].values())


def test_rds_no_disclosure():
    """Given all flags false, when computing RDS, then score is 0."""
    result = compute_rds(model_name="test_model")

    assert "error" not in result
    assert result["rds_score"] == 0
    assert all(v == 0 for v in result["breakdown"].values())


def test_rds_partial_disclosure():
    """Given some flags true, when computing RDS, then score is between 0 and 6."""
    result = compute_rds(
        model_name="test_model",
        has_code=True,
        dataset_available=True,
    )

    assert "error" not in result
    assert 0 < result["rds_score"] <= 6
    assert result["breakdown"]["code_availability"] > 0
    assert result["breakdown"]["data_availability"] > 0
    assert result["breakdown"]["reproducibility"] == 0


def test_rds_breakdown_dimensions():
    """Given valid input, when computing RDS, then all 3 dimensions are present."""
    result = compute_rds(model_name="m")

    assert "code_availability" in result["breakdown"]
    assert "data_availability" in result["breakdown"]
    assert "reproducibility" in result["breakdown"]
    assert len(result["breakdown"]) == 3


def test_rds_empty_model_name():
    """Given empty model name, when computing RDS, then error returned."""
    assert "error" in compute_rds(model_name="")
