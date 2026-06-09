resource "aws_s3_bucket" "results" {
  bucket = "${var.name_prefix}-forecast-results"

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-forecast-results"
  })
}

resource "aws_s3_bucket_lifecycle_configuration" "results" {
  bucket = aws_s3_bucket.results.id

  rule {
    id     = "expire-results"
    status = "Enabled"

    filter {
      prefix = ""
    }

    expiration {
      days = var.results_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "results" {
  bucket = aws_s3_bucket.results.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "results" {
  bucket = aws_s3_bucket.results.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_ebs_volume" "timescaledb" {
  availability_zone = var.availability_zone
  size              = var.db_volume_size_gb
  type              = "gp3"

  tags = merge(var.tags, {
    Name       = "${var.name_prefix}-timescaledb"
    Persistent = "true"
  })
}

resource "aws_iam_policy" "s3_write" {
  name        = "${var.name_prefix}-s3-write"
  description = "Allow writing forecast results to S3"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.results.arn,
          "${aws_s3_bucket.results.arn}/*"
        ]
      }
    ]
  })
}

resource "aws_iam_policy" "ebs_attach" {
  name        = "${var.name_prefix}-ebs-attach"
  description = "Allow attaching the persistent EBS volume"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:AttachVolume",
          "ec2:DetachVolume",
          "ec2:DescribeVolumes"
        ]
        Resource = [
          aws_ebs_volume.timescaledb.arn
        ]
      }
    ]
  })
}
