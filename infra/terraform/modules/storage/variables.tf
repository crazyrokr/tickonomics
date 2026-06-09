variable "region" {
  description = "AWS region"
  type        = string
}

variable "availability_zone" {
  description = "Availability zone for EBS volume"
  type        = string
}

variable "db_volume_size_gb" {
  description = "EBS volume size in GB"
  type        = number
  default     = 50
}

variable "results_retention_days" {
  description = "S3 lifecycle expiration in days"
  type        = number
  default     = 30
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
