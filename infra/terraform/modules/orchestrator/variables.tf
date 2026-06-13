variable "region" {
  description = "AWS region"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for Lambda VPC config"
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security group IDs for Lambda functions"
  type        = list(string)
}

variable "results_bucket_name" {
  description = "S3 bucket name for forecast results"
  type        = string
}

variable "results_bucket_arn" {
  description = "S3 bucket ARN for forecast results"
  type        = string
}

variable "compute_spot_module_source" {
  description = "Path to compute-spot module for terraform apply from Lambda"
  type        = string
  default     = ""
}

variable "schedule_expression" {
  description = "EventBridge schedule expression"
  type        = string
  default     = "cron(0 6 ? * MON-FRI *)"
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
