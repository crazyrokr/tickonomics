variable "monthly_budget_amount" {
  description = "Monthly budget amount in USD"
  type        = number
}

variable "alert_email" {
  description = "Email address for budget alerts"
  type        = string
}

variable "environment_name" {
  description = "Environment label for the budget name"
  type        = string
  default     = "production"

  validation {
    condition     = contains(["staging", "production", "forecast"], var.environment_name)
    error_message = "Environment must be 'staging', 'production', or 'forecast'."
  }
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
