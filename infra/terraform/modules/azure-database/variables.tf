variable "resource_group_name" {
  description = "Resource group containing the managed disk"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "availability_zone" {
  description = "Azure availability zone (must match the spot VM)"
  type        = string
}

variable "db_volume_size_gb" {
  description = "Managed disk size in GB"
  type        = number
  default     = 50
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
