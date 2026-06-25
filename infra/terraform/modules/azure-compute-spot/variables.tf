variable "resource_group_name" {
  description = "Resource group containing the spot VM"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "availability_zone" {
  description = "Azure availability zone (must match the managed disk)"
  type        = string
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

variable "subnet_id" {
  description = "Subnet ID for the spot VM network interface"
  type        = string
}

variable "managed_disk_id" {
  description = "Managed disk ID to attach for TimescaleDB data"
  type        = string
}

variable "ssh_public_key" {
  description = "SSH public key for Bastion-tunneled VM access"
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
