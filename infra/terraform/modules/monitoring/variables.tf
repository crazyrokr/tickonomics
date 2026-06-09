variable "region" {
  description = "AWS region"
  type        = string
}

variable "alert_email" {
  description = "Email address for SNS alert notifications"
  type        = string
  default     = ""
}

variable "spot_instance_id" {
  description = "Spot instance ID to monitor (dynamic, may be empty)"
  type        = string
  default     = ""
}

variable "log_group_names" {
  description = "CloudWatch log group names to monitor"
  type        = list(string)
  default     = []
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
