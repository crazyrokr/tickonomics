variable "region" {
  description = "AWS region for the state backend (must match environments/aws-forecast/backend.tf and environments/aws-production/backend.tf)"
  type        = string
  default     = "us-east-1"
}

variable "state_bucket" {
  description = "Globally-unique S3 bucket that stores Terraform remote state (must match environments/aws-forecast/backend.tf and environments/aws-production/backend.tf)"
  type        = string
  default     = "tickonomics-terraform-state"
}

variable "lock_table" {
  description = "DynamoDB table used for Terraform state locking (must match environments/aws-forecast/backend.tf and environments/aws-production/backend.tf)"
  type        = string
  default     = "tickonomics-terraform-locks"
}

variable "tags" {
  description = "Common tags applied to all resources"
  type        = map(string)
  default     = {}
}
