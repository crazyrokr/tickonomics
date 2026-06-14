resource "aws_kms_key" "secrets" {
  description             = "KMS key for SSM SecureString encryption"
  enable_key_rotation     = true
  rotation_period_in_days = 90

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-secrets-key"
  })
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/tickonomics/db/password"
  description = "TimescaleDB password"
  type        = "SecureString"
  key_id      = aws_kms_key.secrets.id
  value       = var.db_password

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-ssm-db-password"
  })
}

resource "aws_ssm_parameter" "finnhub_api_key" {
  name        = "/tickonomics/api/finnhub"
  description = "Finnhub API key"
  type        = "SecureString"
  key_id      = aws_kms_key.secrets.id
  value       = var.finnhub_api_key

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-ssm-finnhub-key"
  })
}

resource "aws_ssm_parameter" "alphavantage_api_key" {
  name        = "/tickonomics/api/alphavantage"
  description = "Alpha Vantage API key"
  type        = "SecureString"
  key_id      = aws_kms_key.secrets.id
  value       = var.alphavantage_api_key

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-ssm-alphavantage-key"
  })
}

resource "aws_ssm_parameter" "fred_api_key" {
  name        = "/tickonomics/api/fred"
  description = "FRED API key"
  type        = "SecureString"
  key_id      = aws_kms_key.secrets.id
  value       = var.fred_api_key

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-ssm-fred-key"
  })
}

resource "aws_ssm_parameter" "oauth_client_secret" {
  name        = "/tickonomics/oauth/client-secret"
  description = "OAuth2 / Keycloak client secret"
  type        = "SecureString"
  key_id      = aws_kms_key.secrets.id
  value       = var.oauth_client_secret

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-ssm-oauth-secret"
  })
}

resource "aws_ssm_parameter" "ecr_token" {
  name        = "/tickonomics/registry/token"
  description = "ECR auth token placeholder (refreshed by instance)"
  type        = "SecureString"
  key_id      = aws_kms_key.secrets.id
  value       = "placeholder"

  lifecycle {
    ignore_changes = [value]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-ssm-ecr-token"
  })
}

resource "aws_iam_policy" "ssm_read" {
  name        = "${var.name_prefix}-ssm-read"
  description = "Allow reading tickonomics secrets from SSM Parameter Store"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = [
          "arn:aws:ssm:*:*:parameter/tickonomics/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt"
        ]
        Resource = [
          aws_kms_key.secrets.arn
        ]
      }
    ]
  })
}

output "kms_key_arn" {
  description = "ARN of the KMS key for SSM encryption"
  value       = aws_kms_key.secrets.arn
}

output "ssm_read_policy_arn" {
  description = "ARN of the IAM policy granting SSM read access"
  value       = aws_iam_policy.ssm_read.arn
}
