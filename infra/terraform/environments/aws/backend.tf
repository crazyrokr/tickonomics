terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "tickonomics-terraform-state"
    key            = "forecast/aws/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tickonomics-terraform-locks"
    encrypt        = true
  }
}
