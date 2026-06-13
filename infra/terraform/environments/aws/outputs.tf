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
