variable "instance_id" {
  description = "ID of the hosting EC2 instance"
  type        = string
}

variable "db_volume_id" {
  description = "ID of the TimescaleDB EBS volume for snapshots"
  type        = string
}

variable "backup_bucket_name" {
  description = "Name for the S3 backup bucket"
  type        = string
}

variable "retention_days" {
  description = "Number of days to retain backups"
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
