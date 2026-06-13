import json
import os
import uuid
import logging
import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

ec2 = boto3.client("ec2")


def handler(event, context):
    logger.info("Event: %s", json.dumps(event))

    task_id = event.get("body", {})
    if isinstance(task_id, str):
        try:
            task_id = json.loads(task_id).get("task_id", str(uuid.uuid4()))
        except (json.JSONDecodeError, AttributeError):
            task_id = str(uuid.uuid4())
    elif isinstance(task_id, dict):
        task_id = task_id.get("task_id", str(uuid.uuid4()))
    else:
        task_id = str(uuid.uuid4())

    task_id = f"{task_id}" if task_id else str(uuid.uuid4())

    try:
        response = {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({
                "task_id": task_id,
                "status": "TRIGGERED",
                "message": "Forecast spot instance launch initiated",
            }),
        }
        logger.info("Triggered forecast task: %s", task_id)
        return response
    except Exception as e:
        logger.error("Failed to trigger forecast: %s", str(e))
        return {
            "statusCode": 500,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({
                "task_id": task_id,
                "status": "FAILED",
                "message": str(e),
            }),
        }
