terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # State key is stack-specific; the bucket/lock table are bootstrapped once
  # (see infra/terraform/bootstrap/ and ADR-038). aws-production reads this state.
  backend "s3" {
    bucket         = "tickonomics-terraform-state"
    key            = "aws-forecast/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tickonomics-terraform-locks"
    encrypt        = true
  }
}
