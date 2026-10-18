provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "tickonomics"
      Environment = "prod"
      Service     = "tickonomics"
      ManagedBy   = "terraform"
    }
  }
}
