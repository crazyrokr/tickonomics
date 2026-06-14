import json

import pytest

import conftest
import forecast_trigger


@pytest.fixture(autouse=True)
def _fresh(monkeypatch):
    conftest.reset_fakes()
    monkeypatch.setenv("LAUNCH_TEMPLATE_ID", "lt-abcdef")
    monkeypatch.setenv("SUBNET_ID", "subnet-1")
    monkeypatch.setenv("SECURITY_GROUP_IDS", "sg-1,sg-2")
    monkeypatch.setenv("RESULTS_BUCKET", "results-bucket")
    monkeypatch.delenv("SPOT_PRICE_MAX", raising=False)
    yield


def test_trigger_launches_spot_and_writes_running_status():
    # Given a valid trigger event and a successful run_instances
    event = {"body": json.dumps({"task_id": "task-123"})}

    # When the handler runs
    response = forecast_trigger.handler(event, None)

    # Then it returns 200 with the task and instance ids and a RUNNING status marker
    body = json.loads(response["body"])
    assert response["statusCode"] == 200
    assert body["status"] == "TRIGGERED"
    assert body["task_id"] == "task-123"
    assert body["instance_id"] == "i-testinstance"
    put = [c for c in conftest.fake_s3().put_calls if c["Key"] == "task-123/STATUS"]
    assert put and put[0]["Body"] == b"RUNNING"


def test_trigger_uses_launch_template_with_spot_market_options():
    # Given a trigger event
    event = {"body": "{}"}

    # When the handler runs
    forecast_trigger.handler(event, None)

    # Then run_instances is called exactly once with the launch template + spot options
    calls = conftest.fake_ec2().run_instances_calls
    assert len(calls) == 1
    spec = calls[0]
    assert spec["LaunchTemplate"] == {"LaunchTemplateId": "lt-abcdef", "Version": "$Default"}
    assert spec["SubnetId"] == "subnet-1"
    assert spec["SecurityGroupIds"] == ["sg-1", "sg-2"]
    assert spec["InstanceMarketOptions"]["MarketType"] == "spot"
    spot = spec["InstanceMarketOptions"]["SpotOptions"]
    assert spot["SpotInstanceType"] == "one-time"
    assert spot["InstanceInterruptionBehavior"] == "terminate"
    tags = spec["TagSpecifications"][0]["Tags"]
    assert {"Key": "Name", "Value": "tickonomics-spot-forecast"} in tags


def test_trigger_includes_max_price_when_configured(monkeypatch):
    # Given a configured spot price ceiling
    monkeypatch.setenv("SPOT_PRICE_MAX", "0.27")
    event = {"body": "{}"}

    # When the handler runs
    forecast_trigger.handler(event, None)

    # Then the spot options carry the MaxPrice bid
    spot = conftest.fake_ec2().run_instances_calls[0]["InstanceMarketOptions"]["SpotOptions"]
    assert spot["MaxPrice"] == "0.27"


def test_trigger_omits_max_price_when_blank():
    # Given no spot price ceiling
    event = {"body": "{}"}

    # When the handler runs
    forecast_trigger.handler(event, None)

    # Then the spot options do not carry a MaxPrice bid
    spot = conftest.fake_ec2().run_instances_calls[0]["InstanceMarketOptions"]["SpotOptions"]
    assert "MaxPrice" not in spot


def test_trigger_generates_task_id_when_missing():
    # Given an event with no task_id in the body
    event = {"body": "{}"}

    # When the handler runs
    response = forecast_trigger.handler(event, None)

    # Then a non-empty task_id is generated
    body = json.loads(response["body"])
    assert body["task_id"]
    assert body["task_id"] != "{}"


def test_trigger_writes_failed_status_and_returns_500_on_error():
    # Given run_instances fails
    conftest.fake_ec2().run_instances_error = RuntimeError("boom")
    event = {"body": json.dumps({"task_id": "task-9"})}

    # When the handler runs
    response = forecast_trigger.handler(event, None)

    # Then it returns 500 and writes a FAILED status marker
    body = json.loads(response["body"])
    assert response["statusCode"] == 500
    assert body["status"] == "FAILED"
    put = [c for c in conftest.fake_s3().put_calls if c["Key"] == "task-9/STATUS"]
    assert put and put[0]["Body"] == b"FAILED"
