resource "aws_instance" "hosting" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = var.security_group_ids
  iam_instance_profile   = aws_iam_instance_profile.hosting.name
  key_name               = aws_key_pair.emergency.key_name

  root_block_device {
    volume_size           = var.ebs_root_size_gb
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true

    tags = merge(var.tags, {
      Name = "${var.name_prefix}-${var.environment}-root"
    })
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = merge(var.tags, {
    Name        = "${var.name_prefix}-${var.environment}"
    Environment = var.environment
  })
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_eip" "hosting" {
  instance = aws_instance.hosting.id
  domain   = "vpc"

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${var.environment}-eip"
  })
}

resource "aws_key_pair" "emergency" {
  key_name   = "${var.name_prefix}-${var.environment}-key"
  public_key = var.ssh_public_key

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${var.environment}-key"
  })
}

resource "aws_volume_attachment" "timescaledb" {
  device_name = "/dev/sdf"
  volume_id   = var.ebs_db_volume_id
  instance_id = aws_instance.hosting.id
}

resource "aws_iam_role" "hosting" {
  name = "${var.name_prefix}-${var.environment}-hosting-role"

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

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${var.environment}-hosting-role"
  })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.hosting.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ecr_readonly" {
  role       = aws_iam_role.hosting.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "s3_write" {
  role       = aws_iam_role.hosting.name
  policy_arn = var.s3_write_policy_arn
}

resource "aws_iam_role_policy_attachment" "ssm_read" {
  count      = var.ssm_read_policy_arn != "" ? 1 : 0
  role       = aws_iam_role.hosting.name
  policy_arn = var.ssm_read_policy_arn
}

resource "aws_iam_instance_profile" "hosting" {
  name = "${var.name_prefix}-${var.environment}-hosting-profile"
  role = aws_iam_role.hosting.name

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${var.environment}-hosting-profile"
  })
}

resource "aws_security_group" "hosting" {
  name_prefix = "${var.name_prefix}-${var.environment}-hosting-"
  description = "Security group for persistent hosting instance (${var.environment})"
  vpc_id      = data.aws_subnet.selected.vpc_id

  ingress {
    description = "HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP from anywhere (Caddy redirect to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${var.environment}-hosting-sg"
  })
}

data "aws_subnet" "selected" {
  id = var.subnet_id
}

output "instance_id" {
  description = "ID of the hosting EC2 instance"
  value       = aws_instance.hosting.id
}

output "instance_public_ip" {
  description = "Public IP of the hosting instance"
  value       = aws_eip.hosting.public_ip
}

output "elastic_ip" {
  description = "Elastic IP address for DNS records"
  value       = aws_eip.hosting.public_ip
}

output "iam_role_arn" {
  description = "ARN of the hosting IAM role"
  value       = aws_iam_role.hosting.arn
}

output "security_group_id" {
  description = "Security group ID for the hosting instance"
  value       = aws_security_group.hosting.id
}
