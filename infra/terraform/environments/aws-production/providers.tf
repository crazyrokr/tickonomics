provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "tickonomics"
      Environment = "Prod"
      Service     = "tickonomics"
      ManagedBy   = "terraform"
    }
  }
}
