import json
import os
import time
from datetime import datetime, timezone

import boto3

ec2 = boto3.client("ec2")
ssm = boto3.client("ssm")
s3 = boto3.client("s3")


def handler(event, context):
    instance_id = os.environ["INSTANCE_ID"]
    db_volume_id = os.environ["DB_VOLUME_ID"]
    backup_bucket = os.environ["BACKUP_BUCKET"]
    retention_days = int(os.environ["RETENTION_DAYS"])
    name_prefix = os.environ.get("NAME_PREFIX", "tickonomics")
    date_str = datetime.now(timezone.utc).strftime("%Y%m%d")

    print(f"Starting daily backup: {date_str}")

    # 1. Create EBS snapshot
    print(f"Creating snapshot of volume {db_volume_id}")
    snapshot = ec2.create_snapshot(
        VolumeId=db_volume_id,
        Description=f"{name_prefix}-daily-{date_str}",
        TagSpecifications=[{
            "ResourceType": "snapshot",
            "Tags": [
                {"Key": "Name", "Value": f"{name_prefix}-snapshot-{date_str}"},
                {"Key": "AutoBackup", "Value": "true"},
            ]
        }]
    )
    snapshot_id = snapshot["SnapshotId"]
    print(f"Snapshot created: {snapshot_id}")

    # 2. Run pg_dump via SSM
    print("Starting pg_dump via SSM")
    command = (
        f"docker exec tickonomics-timescaledb-1 pg_dump -U tickonomics "
        f"--format=custom | gzip | "
        f"aws s3 cp - s3://{backup_bucket}/pg_dump/{date_str}.dump.gz"
    )
    response = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={"commands": [command]},
        TimeoutSeconds=300,
    )
    command_id = response["Command"]["CommandId"]
    print(f"SSM command sent: {command_id}")

    # Wait for completion
    for _ in range(60):
        result = ssm.get_command_invocation(
            CommandId=command_id,
            InstanceId=instance_id,
        )
        if result["Status"] in ("Success", "Failed", "TimedOut", "Cancelled"):
            print(f"pg_dump finished: {result['Status']}")
            break
        time.sleep(10)
    else:
        print("pg_dump timed out waiting for SSM")

    # 3. Clean old snapshots
    print(f"Cleaning snapshots older than {retention_days} days")
    snapshots = ec2.describe_snapshots(
        Filters=[
            {"Name": "tag:AutoBackup", "Values": ["true"]},
        ],
        OwnerIds=["self"],
    )
    cutoff = datetime.now(timezone.utc).timestamp() - (retention_days * 86400)
    deleted = 0
    for snap in snapshots["Snapshots"]:
        if snap["StartTime"].timestamp() < cutoff:
            ec2.delete_snapshot(SnapshotId=snap["SnapshotId"])
            deleted += 1
    print(f"Deleted {deleted} old snapshots")

    print("Backup completed successfully")
    return {"statusCode": 200, "body": json.dumps({
        "status": "COMPLETED",
        "snapshot_id": snapshot_id,
        "date": date_str,
        "deleted_snapshots": deleted,
    })}
