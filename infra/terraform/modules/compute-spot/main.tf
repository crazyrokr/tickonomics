data "aws_caller_identity" "current" {}

resource "aws_iam_role" "spot" {
  name = "${var.name_prefix}-spot-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.spot.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "s3_write" {
  role       = aws_iam_role.spot.name
  policy_arn = var.s3_write_policy_arn
}

resource "aws_iam_role_policy_attachment" "ebs_attach" {
  role       = aws_iam_role.spot.name
  policy_arn = var.ebs_attach_policy_arn
}

resource "aws_iam_role_policy" "ecr_read" {
  name = "${var.name_prefix}-ecr-read"
  role = aws_iam_role.spot.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy" "self_terminate" {
  name = "${var.name_prefix}-self-terminate"
  role = aws_iam_role.spot.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:TerminateInstances"
        ]
        Resource = "arn:aws:ec2:${var.region}:${data.aws_caller_identity.current.account_id}:instance/*"
        Condition = {
          StringEquals = {
            "ec2:ResourceTag/Name" = "${var.name_prefix}-spot-forecast"
          }
        }
      }
    ]
  })
}

resource "aws_iam_instance_profile" "spot" {
  name = "${var.name_prefix}-spot-profile"
  role = aws_iam_role.spot.name
}

locals {
  shared_scripts = "${path.module}/../../shared/scripts"
}

data "cloudinit_config" "forecast" {
  gzip          = true
  base64_encode = true

  part {
    content_type = "text/cloud-config"
    content = templatefile("${path.module}/cloud-config.yaml.tftpl", {
      registry_url               = var.registry_url
      backend_image              = var.backend_image
      analytics_image            = var.analytics_image
      postgres_password          = var.postgres_password
      finnhub_api_key            = var.finnhub_api_key
      alphavantage_api_key       = var.alphavantage_api_key
      fred_api_key               = var.fred_api_key
      results_bucket             = var.results_bucket
      aws_region                 = var.region
      auto_terminate             = var.auto_terminate
      forecast_task_script_b64   = base64encode(file("${local.shared_scripts}/forecast-task.sh"))
      health_check_script_b64    = base64encode(file("${local.shared_scripts}/health-check.sh"))
      collect_results_script_b64 = base64encode(file("${local.shared_scripts}/collect-results.sh"))
      shutdown_script_b64        = base64encode(file("${local.shared_scripts}/shutdown.sh"))
    })
  }
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_launch_template" "forecast" {
  name                   = "${var.name_prefix}-forecast-spot"
  image_id               = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  update_default_version = true

  iam_instance_profile {
    name = aws_iam_instance_profile.spot.name
  }

  user_data = data.cloudinit_config.forecast.rendered

  block_device_mappings {
    device_name = "/dev/sda1"
    ebs {
      volume_size           = 30
      volume_type           = "gp3"
      delete_on_termination = true
    }
  }

  tag_specifications {
    resource_type = "instance"
    tags = merge(var.tags, {
      Name = "${var.name_prefix}-spot-forecast"
    })
  }
}

resource "aws_spot_instance_request" "forecast" {
  spot_price           = var.spot_price_max
  spot_type            = "one-time"
  wait_for_fulfillment = true
  valid_until          = timeadd(timestamp(), "4h")

  subnet_id              = var.subnet_id
  vpc_security_group_ids = var.security_group_ids

  launch_template {
    id      = aws_launch_template.forecast.id
    version = "$Default"
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-spot-forecast"
  })
}

resource "aws_volume_attachment" "timescaledb" {
  device_name = "/dev/sdf"
  volume_id   = var.ebs_volume_id
  instance_id = aws_spot_instance_request.forecast.spot_instance_id
}
