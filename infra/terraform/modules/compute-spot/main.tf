data "aws_caller_identity" "current" {}

resource "aws_key_pair" "spot" {
  key_name   = "${var.name_prefix}-spot-key"
  public_key = var.ssh_public_key

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-spot-key"
  })
}

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

data "cloudinit_config" "forecast" {
  gzip          = true
  base64_encode = true

  part {
    content_type = "text/cloud-config"
    content = templatefile("${path.module}/cloud-config.yaml.tftpl", {
      registry_url      = var.registry_url
      backend_image     = var.backend_image
      analytics_image   = var.analytics_image
      postgres_password = var.postgres_password
      polygon_api_key   = var.polygon_api_key
      fred_api_key      = var.fred_api_key
      results_bucket    = var.results_bucket
      aws_region        = var.region
      auto_terminate    = var.auto_terminate
    })
  }

  part {
    content_type = "text/x-shellscript"
    content      = file("${path.module}/../../shared/scripts/forecast-task.sh")
  }
}

resource "aws_spot_instance_request" "forecast" {
  ami                  = data.aws_ami.ubuntu.id
  instance_type        = var.instance_type
  spot_price           = var.spot_price_max
  spot_type            = "one-time"
  wait_for_fulfillment = true
  valid_until          = timeadd(timestamp(), "4h")

  key_name               = aws_key_pair.spot.key_name
  iam_instance_profile   = aws_iam_instance_profile.spot.name
  subnet_id              = var.subnet_id
  vpc_security_group_ids = var.security_group_ids
  user_data              = data.cloudinit_config.forecast.rendered

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-spot-forecast"
  })
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

resource "aws_volume_attachment" "timescaledb" {
  device_name = "/dev/sdf"
  volume_id   = var.ebs_volume_id
  instance_id = aws_spot_instance_request.forecast.spot_instance_id
}
