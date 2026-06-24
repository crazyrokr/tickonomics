variable "resource_group_name" {
  description = "Resource group containing the function app"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "results_container_name" {
  description = "Blob container where forecast results are uploaded"
  type        = string
}

variable "results_storage_connection_string" {
  description = "Connection string of the results storage account"
  type        = string
  sensitive   = true
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
