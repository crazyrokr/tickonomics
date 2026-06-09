provider "google" {
  project = var.gcp_project_id
  region  = var.region

  default_labels = {
    project     = "tickonomics"
    environment = "forecast"
    managed-by  = "terraform"
  }
}

provider "google-beta" {
  project = var.gcp_project_id
  region  = var.region
}
