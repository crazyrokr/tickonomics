terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Local backend on purpose: Terraform cannot store the state of the resources that back
  # its own remote state inside that same remote backend (chicken-and-egg). This stack owns
  # the S3 state bucket + DynamoDB lock table; the environments consume them, never manage them.
  backend "local" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = merge(var.tags, {
      Project     = "tickonomics"
      Environment = "prod"
      Service     = "tickonomics"
      ManagedBy   = "terraform-bootstrap"
    })
  }
}

resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket

  tags = merge(var.tags, {
    Name = var.state_bucket
  })
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# S3.5: deny non-SSL (HTTP) requests to the state bucket.
resource "aws_s3_bucket_policy" "state_ssl" {
  bucket = aws_s3_bucket.state.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.state.arn,
          "${aws_s3_bucket.state.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}

resource "aws_dynamodb_table" "locks" {
  name         = var.lock_table
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = merge(var.tags, {
    Name = var.lock_table
  })
}
