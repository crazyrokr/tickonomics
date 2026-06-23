output "backend_repository_url" {
  description = "ECR repository URL for the backend image"
  value       = aws_ecr_repository.backend.repository_url
}

output "analytics_repository_url" {
  description = "ECR repository URL for the analytics image"
  value       = aws_ecr_repository.analytics.repository_url
}

output "backend_repository_arn" {
  description = "ECR repository ARN for the backend image"
  value       = aws_ecr_repository.backend.arn
}

output "analytics_repository_arn" {
  description = "ECR repository ARN for the analytics image"
  value       = aws_ecr_repository.analytics.arn
}

output "dashboard_repository_url" {
  description = "ECR repository URL for the dashboard image"
  value       = aws_ecr_repository.dashboard.repository_url
}

output "dashboard_repository_arn" {
  description = "ECR repository ARN for the dashboard image"
  value       = aws_ecr_repository.dashboard.arn
}
