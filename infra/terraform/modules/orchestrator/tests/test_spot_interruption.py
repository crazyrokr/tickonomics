import json

import pytest

import conftest
import spot_interruption


@pytest.fixture(autouse=True)
def _fresh():
    conftest.reset_fakes()
    yield


def test_interruption_writes_marker(monkeypatch):
    # Given a configured bucket and a spot interruption event
    monkeypatch.setattr(spot_interruption, "BUCKET", "results-bucket")
    event = {"detail": {"instance-id": "i-xyz", "instance-action": "stop"}}

    # When the handler runs
    response = spot_interruption.handler(event, None)

    # Then it writes a JSON marker keyed by instance id and returns 200
    assert response["statusCode"] == 200
    put = [c for c in conftest.fake_s3().put_calls if c["Key"] == "_interruption/i-xyz"]
    assert put
    marker = json.loads(put[0]["Body"])
    assert marker["instance_id"] == "i-xyz"
    assert marker["action"] == "stop"


def test_interruption_no_marker_when_bucket_missing(monkeypatch):
    # Given no results bucket configured
    monkeypatch.setattr(spot_interruption, "BUCKET", "")
    event = {"detail": {"instance-id": "i-1", "instance-action": "terminate"}}

    # When the handler runs
    response = spot_interruption.handler(event, None)

    # Then no marker is written but the handler still succeeds
    assert response["statusCode"] == 200
    assert conftest.fake_s3().put_calls == []


def test_interruption_defaults_unknown_instance(monkeypatch):
    # Given an event with no detail block
    monkeypatch.setattr(spot_interruption, "BUCKET", "results-bucket")
    event = {"detail": {}}

    # When the handler runs
    spot_interruption.handler(event, None)

    # Then the marker is keyed under the unknown fallback
    put = [c for c in conftest.fake_s3().put_calls if c["Key"] == "_interruption/unknown"]
    assert put
    marker = json.loads(put[0]["Body"])
    assert marker["instance_id"] == "unknown"
