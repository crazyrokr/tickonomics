import json

import pytest

import conftest
import forecast_status


@pytest.fixture(autouse=True)
def _fresh(monkeypatch):
    conftest.reset_fakes()
    monkeypatch.setattr(forecast_status, "BUCKET", "results-bucket")
    yield


def test_status_running_when_status_object_absent():
    # Given a task with no STATUS object in the bucket
    event = {"pathParameters": {"taskId": "task-pending"}}

    # When the status handler runs
    response = forecast_status.handler(event, None)

    # Then it reports RUNNING with no results
    body = json.loads(response["body"])
    assert response["statusCode"] == 200
    assert body["status"] == "RUNNING"
    assert body["has_results"] is False


def test_status_completed_when_status_and_results_present():
    # Given a task whose STATUS is COMPLETED and a results archive exists
    s3 = conftest.fake_s3()
    s3.objects["task-done/STATUS"] = "COMPLETED"
    s3.objects["task-done/forecast-results.tar.gz"] = b"bytes"
    event = {"pathParameters": {"taskId": "task-done"}}

    # When the status handler runs
    response = forecast_status.handler(event, None)

    # Then it reports COMPLETED with a results URL
    body = json.loads(response["body"])
    assert body["status"] == "COMPLETED"
    assert body["has_results"] is True
    assert body["results_url"] == "s3://results-bucket/task-done/forecast-results.tar.gz"


def test_status_failed_is_reflected():
    # Given a task whose STATUS is FAILED
    s3 = conftest.fake_s3()
    s3.objects["task-fail/STATUS"] = "FAILED"
    event = {"pathParameters": {"taskId": "task-fail"}}

    # When the status handler runs
    response = forecast_status.handler(event, None)

    # Then it reports FAILED with no results
    body = json.loads(response["body"])
    assert body["status"] == "FAILED"
    assert body["has_results"] is False


def test_status_rejects_missing_task_id():
    # Given an event with no taskId
    event = {"pathParameters": {}}

    # When the status handler runs
    response = forecast_status.handler(event, None)

    # Then it returns 400
    assert response["statusCode"] == 400
