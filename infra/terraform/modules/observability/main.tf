resource "aws_ebs_volume" "monitoring" {
  availability_zone = var.availability_zone
  size              = 10
  type              = "gp3"
  encrypted         = true

  tags = merge(var.tags, {
    Name       = "${var.name_prefix}-monitoring-data"
    Persistent = "true"
  })
}

output "ebs_volume_id" {
  description = "ID of the monitoring EBS volume"
  value       = aws_ebs_volume.monitoring.id
}

output "ebs_volume_arn" {
  description = "ARN of the monitoring EBS volume"
  value       = aws_ebs_volume.monitoring.arn
}
