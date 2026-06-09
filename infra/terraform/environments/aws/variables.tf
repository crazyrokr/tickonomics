variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "availability_zone" {
  description = "Availability zone (must match EBS volume)"
  type        = string
  default     = "us-east-1a"
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

variable "db_volume_size_gb" {
  description = "Persistent disk size in GB"
  type        = number
  default     = 50
}

variable "image_tag" {
  description = "Container image tag"
  type        = string
  default     = "latest"
}

variable "postgres_password" {
  description = "TimescaleDB password"
  type        = string
  sensitive   = true
}

variable "polygon_api_key" {
  description = "Polygon WebSocket API key"
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

variable "schedule_expression" {
  description = "EventBridge schedule expression for forecast trigger"
  type        = string
  default     = "cron(0 6 ? * MON-FRI *)"
}

variable "auto_terminate" {
  description = "Self-terminate spot instance after forecast completes"
  type        = bool
  default     = true
}

variable "results_retention_days" {
  description = "S3 object lifecycle retention in days"
  type        = number
  default     = 30
}

variable "ssh_public_key" {
  description = "SSH public key for spot instance access"
  type        = string
}

variable "alert_email" {
  description = "Email address for SNS alerts"
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
