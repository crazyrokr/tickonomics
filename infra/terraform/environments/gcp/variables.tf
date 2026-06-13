variable "gcp_project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "GCP zone (must match persistent disk)"
  type        = string
  default     = "us-central1-a"
}

variable "instance_type" {
  description = "Preemptible VM machine type"
  type        = string
  default     = "c2-standard-8"
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
  description = "Cloud Scheduler cron expression"
  type        = string
  default     = "0 6 * * MON-FRI"
}

variable "auto_terminate" {
  description = "Self-terminate VM after forecast"
  type        = bool
  default     = true
}

variable "results_retention_days" {
  description = "GCS object lifecycle retention in days"
  type        = number
  default     = 30
}

variable "ssh_public_key" {
  description = "SSH public key for VM access"
  type        = string
}

variable "alert_email" {
  description = "Email for notification alerts"
  type        = string
  default     = ""
}

variable "name_prefix" {
  description = "Prefix for all resource names"
  type        = string
  default     = "tickonomics"
}

variable "tags" {
  description = "Common labels applied to all resources"
  type        = map(string)
  default     = {}
}
