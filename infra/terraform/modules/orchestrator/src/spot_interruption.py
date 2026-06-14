import json
import os
import logging
import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.client("s3")
BUCKET = os.environ.get("RESULTS_BUCKET", "")


def handler(event, context):
    logger.info("Spot interruption event: %s", json.dumps(event))

    detail = event.get("detail", {})
    instance_id = detail.get("instance-id", "unknown")
    instance_action = detail.get("instance-action", "unknown")

    logger.warning(
        "Spot instance %s received %s action",
        instance_id,
        instance_action,
    )

    marker_key = f"_interruption/{instance_id}"
    try:
        if BUCKET:
            s3.put_object(
                Bucket=BUCKET,
                Key=marker_key,
                Body=json.dumps({
                    "instance_id": instance_id,
                    "action": instance_action,
                    "event": detail,
                }),
                ContentType="application/json",
            )
    except Exception as e:
        logger.error("Failed to log interruption marker: %s", str(e))

    return {"statusCode": 200, "body": json.dumps({"logged": True})}
