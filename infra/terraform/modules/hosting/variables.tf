variable "region" {
  description = "AWS region"
  type        = string
}

variable "availability_zone" {
  description = "Availability zone for the hosting instance"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type for hosting"
  type        = string
  default     = "t3.xlarge"
}

variable "subnet_id" {
  description = "Subnet ID to launch the instance in"
  type        = string
}

variable "security_group_ids" {
  description = "Security group IDs for the instance"
  type        = list(string)
}

variable "ebs_root_size_gb" {
  description = "Root EBS volume size in GB"
  type        = number
  default     = 100
}

variable "ebs_db_volume_id" {
  description = "EBS volume ID to attach for TimescaleDB data"
  type        = string
}

variable "ssh_public_key" {
  description = "SSH public key for emergency instance access"
  type        = string
}

variable "registry_url" {
  description = "ECR registry URL for image pulls"
  type        = string
}

variable "backend_image" {
  description = "Full backend image reference (registry/repo:tag)"
  type        = string
}

variable "analytics_image" {
  description = "Full analytics image reference (registry/repo:tag)"
  type        = string
}

variable "dashboard_image" {
  description = "Full dashboard image reference (registry/repo:tag)"
  type        = string
}

variable "landing_image" {
  description = "Full landing image reference (registry/repo:tag)"
  type        = string
  default     = ""
}

variable "postgres_password" {
  description = "TimescaleDB password"
  type        = string
  sensitive   = true
}

variable "finnhub_api_key" {
  description = "Finnhub REST/WebSocket API key (v6 free data source)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "alphavantage_api_key" {
  description = "Alpha Vantage API key (v6 free data source)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "fred_api_key" {
  description = "FRED API key"
  type        = string
  sensitive   = true
  default     = ""
}

variable "grafana_password" {
  description = "Grafana admin password"
  type        = string
  sensitive   = true
}

variable "results_bucket" {
  description = "S3 bucket name for forecast results"
  type        = string
}

variable "s3_write_policy_arn" {
  description = "ARN of the S3 write policy to attach"
  type        = string
}

variable "ssm_read_policy_arn" {
  description = "ARN of the SSM read policy to attach"
  type        = string
  default     = ""
}

variable "environment" {
  description = "Deployment environment (staging or production)"
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "Environment must be 'staging' or 'production'."
  }
}

variable "domain_name" {
  description = "DNS domain name for this environment"
  type        = string
  default     = ""
}

variable "name_prefix" {
  description = "Prefix for all resource names"
  type        = string
  default     = "tickonomics"
}

variable "tags" {
  description = "Common tags applied to all resources"
  type        = map(string)
  default     = {}
}
