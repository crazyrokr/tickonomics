data "aws_caller_identity" "current" {}

locals {
  # Default to trusting the account root so any IAM principal in the account can assume; narrow
  # by setting var.trust_principals to specific user/role ARNs.
  effective_trust_principals = length(var.trust_principals) > 0 ? var.trust_principals : [
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
  ]
}

resource "aws_iam_role" "admin" {
  name = "${var.name_prefix}-admin"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          AWS = local.effective_trust_principals
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  max_session_duration = var.session_duration_seconds

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-admin"
  })
}

resource "aws_iam_role_policy_attachment" "admin" {
  role       = aws_iam_role.admin.name
  policy_arn = var.managed_policy_arn
}
