variable "region" {
  description = "AWS region"
  type        = string
}

variable "subnet_id" {
  description = "Subnet ID in which the trigger Lambda launches spot instances"
  type        = string
}

variable "security_group_ids" {
  description = "Security group IDs applied to launched spot instances"
  type        = list(string)
}

variable "launch_template_id" {
  description = "Launch template ID the trigger Lambda uses to launch spot instances"
  type        = string
}

variable "spot_price_max" {
  description = "Maximum spot bid price (USD/hr) the trigger Lambda bids"
  type        = string
  default     = "0.30"
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
