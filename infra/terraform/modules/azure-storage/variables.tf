variable "resource_group_name" {
  description = "Resource group containing the storage resources"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "results_retention_days" {
  description = "Blob lifecycle retention in days"
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
