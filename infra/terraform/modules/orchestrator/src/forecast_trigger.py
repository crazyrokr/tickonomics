import json
import os
import uuid
import logging
import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

ec2 = boto3.client("ec2")
s3 = boto3.client("s3")


def _parse_task_id(event):
    body = event.get("body", {})
    if isinstance(body, str):
        try:
            body = json.loads(body)
        except (json.JSONDecodeError, TypeError):
            body = {}
    if isinstance(body, dict):
        task_id = body.get("task_id")
    else:
        task_id = None
    return task_id if task_id else str(uuid.uuid4())


def _launch_spec():
    security_groups = [s for s in os.environ.get("SECURITY_GROUP_IDS", "").split(",") if s]

    spot_options = {
        "SpotInstanceType": "one-time",
        "InstanceInterruptionBehavior": "terminate",
    }
    max_price = os.environ.get("SPOT_PRICE_MAX", "").strip()
    if max_price:
        spot_options["MaxPrice"] = max_price

    spec = {
        "LaunchTemplate": {
            "LaunchTemplateId": os.environ["LAUNCH_TEMPLATE_ID"],
            "Version": "$Default",
        },
        "MinCount": 1,
        "MaxCount": 1,
        "InstanceMarketOptions": {
            "MarketType": "spot",
            "SpotOptions": spot_options,
        },
        "TagSpecifications": [
            {
                "ResourceType": "instance",
                "Tags": [
                    {
                        "Key": "Name",
                        "Value": os.environ.get("INSTANCE_NAME", "tickonomics-spot-forecast"),
                    }
                ],
            }
        ],
    }

    subnet_id = os.environ.get("SUBNET_ID", "").strip()
    if subnet_id:
        spec["SubnetId"] = subnet_id
    if security_groups:
        spec["SecurityGroupIds"] = security_groups

    return spec


def _write_status(task_id, status):
    bucket = os.environ.get("RESULTS_BUCKET", "")
    if not bucket:
        return
    s3.put_object(
        Bucket=bucket,
        Key=f"{task_id}/STATUS",
        Body=status.encode("utf-8"),
    )


def _response(status_code, payload):
    return {
        "statusCode": status_code,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps(payload),
    }


def handler(event, context):
    logger.info("Event: %s", json.dumps(event))
    task_id = _parse_task_id(event)

    try:
        response = ec2.run_instances(**_launch_spec())
        instance_id = response["Instances"][0]["InstanceId"]
        _write_status(task_id, "RUNNING")
        logger.info("Launched spot instance %s for task %s", instance_id, task_id)
        return _response(200, {
            "task_id": task_id,
            "instance_id": instance_id,
            "status": "TRIGGERED",
            "message": "Forecast spot instance launch initiated",
        })
    except Exception as e:
        logger.error("Failed to trigger forecast: %s", str(e))
        _write_status(task_id, "FAILED")
        return _response(500, {
            "task_id": task_id,
            "status": "FAILED",
            "message": str(e),
        })
