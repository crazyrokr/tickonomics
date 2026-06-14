resource "aws_s3_bucket" "backups" {
  bucket = var.backup_bucket_name

  tags = merge(var.tags, {
    Name = var.backup_bucket_name
  })
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id

  rule {
    id     = "expire-backups"
    status = "Enabled"

    filter {
      prefix = ""
    }

    expiration {
      days = var.retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "backups" {
  bucket = aws_s3_bucket.backups.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_iam_role" "backup_runner" {
  name = "${var.name_prefix}-backup-runner"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-backup-runner-role"
  })
}

resource "aws_iam_role_policy" "backup_runner" {
  name = "${var.name_prefix}-backup-runner-policy"
  role = aws_iam_role.backup_runner.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:CreateSnapshot",
          "ec2:DescribeSnapshots",
          "ec2:DeleteSnapshot",
          "ec2:CreateTags"
        ]
        Resource = ["*"]
      },
      {
        Effect = "Allow"
        Action = [
          "ssm:SendCommand",
          "ssm:GetCommandInvocation"
        ]
        Resource = [
          "arn:aws:ssm:*:*:document/AWS-RunShellScript",
          "arn:aws:ec2:*:*:instance/${var.instance_id}"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.backups.arn,
          "${aws_s3_bucket.backups.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = ["arn:aws:logs:*:*:*"]
      }
    ]
  })
}

resource "aws_lambda_function" "backup_runner" {
  filename         = data.archive_file.backup_runner.output_path
  function_name    = "${var.name_prefix}-backup-runner"
  role             = aws_iam_role.backup_runner.arn
  handler          = "backup_runner.handler"
  runtime          = "python3.12"
  timeout          = 300
  memory_size      = 256
  source_code_hash = data.archive_file.backup_runner.output_base64sha256

  environment {
    variables = {
      INSTANCE_ID    = var.instance_id
      DB_VOLUME_ID   = var.db_volume_id
      BACKUP_BUCKET  = aws_s3_bucket.backups.id
      RETENTION_DAYS = tostring(var.retention_days)
      NAME_PREFIX    = var.name_prefix
    }
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-backup-runner"
  })
}

data "archive_file" "backup_runner" {
  type        = "zip"
  source_file = "${path.module}/src/backup_runner.py"
  output_path = "${path.module}/src/backup_runner.zip"
}

resource "aws_cloudwatch_event_rule" "daily_backup" {
  name                = "${var.name_prefix}-daily-backup"
  description         = "Triggers daily database backup at 03:00 UTC"
  schedule_expression = "cron(0 3 * * ? *)"

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-daily-backup"
  })
}

resource "aws_cloudwatch_event_target" "daily_backup" {
  rule      = aws_cloudwatch_event_rule.daily_backup.name
  target_id = "backup-runner"
  arn       = aws_lambda_function.backup_runner.arn
}

resource "aws_lambda_permission" "daily_backup" {
  statement_id  = "AllowEventBridgeDailyBackup"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.backup_runner.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.daily_backup.arn
}

output "backup_bucket_name" {
  description = "Name of the S3 backup bucket"
  value       = aws_s3_bucket.backups.id
}

output "lambda_function_name" {
  description = "Name of the backup runner Lambda function"
  value       = aws_lambda_function.backup_runner.function_name
}
