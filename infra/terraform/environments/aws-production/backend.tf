terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Separate state key from aws-forecast. The bucket/lock table are bootstrapped once
  # (see infra/terraform/bootstrap/ and ADR-038). Apply aws-forecast before this stack.
  backend "s3" {
    bucket         = "tickonomics-terraform-state"
    key            = "aws-production/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tickonomics-terraform-locks"
    encrypt        = true
  }
}
