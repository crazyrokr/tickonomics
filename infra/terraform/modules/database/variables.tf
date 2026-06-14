variable "availability_zone" {
  description = "Availability zone for the EBS volume"
  type        = string
}

variable "db_volume_size_gb" {
  description = "Size of the TimescaleDB EBS volume in GB"
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
