variable "domain_name" {
  description = "Root domain name for the Route53 hosted zone"
  type        = string
  default     = "tickonomics.io"
}

variable "api_records" {
  description = "Map of API subdomain to IP address (e.g. { api = \"1.2.3.4\", \"api.staging\" = \"5.6.7.8\" })"
  type        = map(string)
  default     = {}
}

variable "app_records" {
  description = "Map of app subdomain to IP address (e.g. { app = \"1.2.3.4\", \"app.staging\" = \"5.6.7.8\" })"
  type        = map(string)
  default     = {}
}

variable "landing_cname_target" {
  description = "CNAME target for the apex domain (e.g. Vercel deployment)"
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
