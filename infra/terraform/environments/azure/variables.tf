variable "azure_subscription_id" {
  description = "Azure subscription ID"
  type        = string
}

variable "region" {
  description = "Azure region"
  type        = string
  default     = "East US"
}

variable "availability_zone" {
  description = "Azure availability zone (must match managed disk)"
  type        = string
  default     = "1"
}

variable "instance_type" {
  description = "Spot VM size"
  type        = string
  default     = "Standard_F8s_v2"
}

variable "spot_price_max" {
  description = "Maximum spot price (USD/hr)"
  type        = string
  default     = "0.30"
}

variable "db_volume_size_gb" {
  description = "Managed disk size in GB"
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

variable "schedule_expression" {
  description = "Timer trigger cron expression"
  type        = string
  default     = "0 0 6 * * 1-5"
}

variable "auto_terminate" {
  description = "Self-terminate VM after forecast"
  type        = bool
  default     = true
}

variable "results_retention_days" {
  description = "Blob lifecycle retention in days"
  type        = number
  default     = 30
}

variable "ssh_public_key" {
  description = "SSH public key for VM access"
  type        = string
}

variable "alert_email" {
  description = "Email for alert notifications"
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
