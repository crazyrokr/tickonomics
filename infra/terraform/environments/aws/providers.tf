provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "tickonomics"
      Environment = "forecast"
      ManagedBy   = "terraform"
    }
  }
}
