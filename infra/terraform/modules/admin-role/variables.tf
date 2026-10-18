variable "name_prefix" {
  description = "Prefix for the admin role name"
  type        = string
  default     = "tickonomics"
}

variable "trust_principals" {
  description = "IAM principal ARNs allowed to assume this role (e.g. your IAM user ARN). Leave empty to trust the whole account root."
  type        = list(string)
  default     = []
}

variable "session_duration_seconds" {
  description = "Max STS session duration for assumed-role credentials (must be >= the client profile's duration_seconds)"
  type        = number
  default     = 3600
}

variable "managed_policy_arn" {
  description = "Managed policy to attach. Defaults to AdministratorAccess; scope down (e.g. PowerUserAccess + IAMFullAccess) for least privilege."
  type        = string
  default     = "arn:aws:iam::aws:policy/AdministratorAccess"
}

variable "tags" {
  description = "Tags applied to the role"
  type        = map(string)
  default     = {}
}
