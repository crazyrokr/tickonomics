variable "region" {
  description = "AWS region (must match aws-forecast)"
  type        = string
  default     = "us-east-1"
}

variable "availability_zone" {
  description = "Availability zone (must match aws-forecast)"
  type        = string
  default     = "us-east-1a"
}

variable "state_bucket" {
  description = "S3 bucket holding aws-forecast remote state (must match backend.tf)"
  type        = string
  default     = "tickonomics-terraform-state"
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

variable "finnhub_api_key" {
  description = "Finnhub REST/WebSocket API key"
  type        = string
  sensitive   = true
  default     = ""
}

variable "alphavantage_api_key" {
  description = "Alpha Vantage API key"
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

variable "ssh_public_key" {
  description = "SSH public key for the hosting instance"
  type        = string
}

variable "grafana_password" {
  description = "Grafana admin password for the observability stack"
  type        = string
  sensitive   = true
  default     = ""
}

variable "oauth_client_secret" {
  description = "OAuth2 / Keycloak client secret"
  type        = string
  sensitive   = true
  default     = ""
}

variable "domain_name" {
  description = "Root domain name for the Route53 hosted zone"
  type        = string
  default     = "tickonomics.io"
}

variable "landing_cname_target" {
  description = "CNAME target for apex domain (e.g. Vercel deployment URL)"
  type        = string
  default     = ""
}

variable "alert_email" {
  description = "Email address for budget/SNS alerts"
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
