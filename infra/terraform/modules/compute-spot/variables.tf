variable "region" {
  description = "AWS region"
  type        = string
}

variable "availability_zone" {
  description = "Availability zone (must match EBS)"
  type        = string
}

variable "instance_type" {
  description = "Spot instance type"
  type        = string
  default     = "c5.2xlarge"
}

variable "spot_price_max" {
  description = "Maximum spot bid price (USD/hr)"
  type        = string
  default     = "0.30"
}

variable "subnet_id" {
  description = "Subnet ID to launch the spot instance in"
  type        = string
}

variable "security_group_ids" {
  description = "Security group IDs for the spot instance"
  type        = list(string)
}

variable "ebs_volume_id" {
  description = "EBS volume ID to attach for TimescaleDB data"
  type        = string
}

variable "ssh_public_key" {
  description = "SSH public key for instance access"
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

variable "postgres_password" {
  description = "TimescaleDB password"
  type        = string
  sensitive   = true
}

variable "polygon_api_key" {
  description = "Polygon API key"
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

variable "results_bucket" {
  description = "S3 bucket name for forecast results"
  type        = string
}

variable "auto_terminate" {
  description = "Self-terminate instance after forecast"
  type        = bool
  default     = true
}

variable "s3_write_policy_arn" {
  description = "ARN of the S3 write policy to attach"
  type        = string
}

variable "ebs_attach_policy_arn" {
  description = "ARN of the EBS attach policy to attach"
  type        = string
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
