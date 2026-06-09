import json
import os
import logging
import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.client("s3")
BUCKET = os.environ.get("RESULTS_BUCKET", "")


def handler(event, context):
    logger.info("Event: %s", json.dumps(event))

    path_params = event.get("pathParameters") or {}
    task_id = path_params.get("taskId", "")

    if not task_id:
        return {
            "statusCode": 400,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"error": "taskId is required"}),
        }

    try:
        try:
            s3.head_object(Bucket=BUCKET, Key=f"{task_id}/STATUS")
            status_obj = s3.get_object(Bucket=BUCKET, Key=f"{task_id}/STATUS")
            status = status_obj["Body"].read().decode("utf-8").strip()
        except s3.exceptions.ClientError as e:
            error_code = e.response["Error"]["Code"]
            if error_code in ("404", "NoSuchKey"):
                status = "RUNNING"
            else:
                raise

        try:
            s3.head_object(Bucket=BUCKET, Key=f"{task_id}/forecast-results.tar.gz")
            has_results = True
        except s3.exceptions.ClientError:
            has_results = False

        response = {
            "task_id": task_id,
            "status": status,
            "has_results": has_results,
        }

        if has_results:
            response["results_url"] = f"s3://{BUCKET}/{task_id}/forecast-results.tar.gz"

        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps(response),
        }
    except Exception as e:
        logger.error("Status check failed: %s", str(e))
        return {
            "statusCode": 500,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"error": str(e), "task_id": task_id}),
        }
