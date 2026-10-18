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
  description = "ECR repository URL for backend image"
  value       = module.container_registry.backend_repository_url
}

output "ecr_analytics_url" {
  description = "ECR repository URL for analytics image"
  value       = module.container_registry.analytics_repository_url
}

output "ecr_dashboard_url" {
  description = "ECR repository URL for dashboard image"
  value       = module.container_registry.dashboard_repository_url
}

output "ebs_volume_id" {
  description = "EBS volume ID attached to the spot instance"
  value       = module.storage.ebs_volume_id
}

output "cloudwatch_dashboard_url" {
  description = "CloudWatch dashboard URL for monitoring"
  value       = module.monitoring.dashboard_url
}

# --- Consumed by aws-production via terraform_remote_state (see ADR-039) ---

output "vpc_id" {
  description = "ID of the shared VPC (consumed by aws-production)"
  value       = module.networking.vpc_id
}

output "public_subnet_id" {
  description = "ID of the public subnet (consumed by aws-production)"
  value       = module.networking.public_subnet_id
}

output "s3_write_policy_arn" {
  description = "ARN of the S3 write IAM policy (consumed by aws-production)"
  value       = module.storage.s3_write_policy_arn
}

output "admin_role_arn" {
  description = "ARN of the assumable operator admin role (use as role_arn in your AWS CLI profile)"
  value       = module.admin_role.admin_role_arn
}

output "admin_role_name" {
  description = "Name of the assumable operator admin role"
  value       = module.admin_role.admin_role_name
}
