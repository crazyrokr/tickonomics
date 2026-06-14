resource "aws_ebs_volume" "timescaledb" {
  availability_zone = var.availability_zone
  size              = var.db_volume_size_gb
  type              = "gp3"
  encrypted         = true

  tags = merge(var.tags, {
    Name       = "${var.name_prefix}-timescaledb-data"
    Persistent = "true"
  })
}

output "ebs_volume_id" {
  description = "ID of the TimescaleDB EBS volume"
  value       = aws_ebs_volume.timescaledb.id
}

output "ebs_volume_arn" {
  description = "ARN of the TimescaleDB EBS volume"
  value       = aws_ebs_volume.timescaledb.arn
}
