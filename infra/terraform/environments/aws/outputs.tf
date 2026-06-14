output "spot_instance_public_ip" {
  description = "Public IP of the spot instance for SSH access"
  value       = module.compute_spot.instance_public_ip
}

output "results_bucket_name" {
  description = "S3 bucket where forecast results are uploaded"
  value       = module.storage.results_bucket_name
}

output "api_gateway_endpoint" {
  description = "API Gateway endpoint for POST /forecast and GET /forecast/{taskId}"
  value       = module.orchestrator.api_endpoint
}

output "ecr_backend_url" {
  description = "ECR repository URL for backend image (CI/CD push)"
  value       = module.container_registry.backend_repository_url
}

output "ecr_analytics_url" {
  description = "ECR repository URL for analytics image (CI/CD push)"
  value       = module.container_registry.analytics_repository_url
}

output "ebs_volume_id" {
  description = "EBS volume ID (survives spot termination)"
  value       = module.storage.ebs_volume_id
}

output "cloudwatch_dashboard_url" {
  description = "CloudWatch dashboard URL for monitoring"
  value       = module.monitoring.dashboard_url
}

# --- Track 14: Production Infrastructure ---

output "hosting_instance_id" {
  description = "EC2 instance ID of the persistent hosting instance"
  value       = module.hosting.instance_id
}

output "hosting_public_ip" {
  description = "Public elastic IP of the hosting instance"
  value       = module.hosting.elastic_ip
}

output "hosting_security_group_id" {
  description = "Security group ID for the hosting instance"
  value       = module.hosting.security_group_id
}

output "db_volume_id" {
  description = "EBS volume ID for TimescaleDB data (persistent hosting)"
  value       = module.database.ebs_volume_id
}

output "route53_zone_id" {
  description = "Route53 hosted zone ID"
  value       = module.dns.zone_id
}

output "route53_name_servers" {
  description = "Route53 name servers"
  value       = module.dns.name_servers
}

output "monitoring_volume_id" {
  description = "EBS volume ID for monitoring data"
  value       = module.observability.ebs_volume_id
}

output "backup_bucket_name" {
  description = "S3 bucket for automated database backups"
  value       = module.backup.backup_bucket_name
}

output "backup_lambda_name" {
  description = "Lambda function name for automated backups"
  value       = module.backup.lambda_function_name
}

output "ssm_kms_key_arn" {
  description = "KMS key ARN for SSM parameter encryption"
  value       = module.secrets.kms_key_arn
}

output "budget_id" {
  description = "AWS Budget ID for cost management"
  value       = module.budget.budget_id
}
