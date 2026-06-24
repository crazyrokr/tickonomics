variable "resource_group_name" {
  description = "Resource group containing the metric alerts"
  type        = string
}

variable "vm_id" {
  description = "ID of the spot forecast VM the alerts are scoped to"
  type        = string
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
