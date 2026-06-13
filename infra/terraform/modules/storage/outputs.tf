output "results_bucket_name" {
  description = "Name of the S3 bucket for forecast results"
  value       = aws_s3_bucket.results.id
}

output "results_bucket_arn" {
  description = "ARN of the S3 bucket for forecast results"
  value       = aws_s3_bucket.results.arn
}

output "ebs_volume_id" {
  description = "EBS volume ID (survives spot termination)"
  value       = aws_ebs_volume.timescaledb.id
}

output "s3_write_policy_arn" {
  description = "ARN of the S3 write IAM policy"
  value       = aws_iam_policy.s3_write.arn
}

output "ebs_attach_policy_arn" {
  description = "ARN of the EBS attach IAM policy"
  value       = aws_iam_policy.ebs_attach.arn
}
