# --- Networking ---
resource "google_compute_network" "main" {
  name                    = "${var.name_prefix}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "public" {
  name          = "${var.name_prefix}-public-subnet"
  network       = google_compute_network.main.id
  region        = var.region
  ip_cidr_range = "10.0.1.0/24"
}

resource "google_compute_firewall" "ssh" {
  name    = "${var.name_prefix}-allow-ssh"
  network = google_compute_network.main.name

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["forecast-spot"]
}

resource "google_compute_firewall" "egress" {
  name      = "${var.name_prefix}-allow-egress"
  network   = google_compute_network.main.name
  direction = "EGRESS"

  allow {
    protocol = "all"
  }

  destination_ranges = ["0.0.0.0/0"]
  target_tags        = ["forecast-spot"]
}

resource "google_compute_router" "main" {
  name    = "${var.name_prefix}-router"
  network = google_compute_network.main.id
  region  = var.region
}

resource "google_compute_router_nat" "main" {
  name                               = "${var.name_prefix}-nat"
  router                             = google_compute_router.main.name
  region                             = var.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}

# --- Storage ---
resource "google_storage_bucket" "results" {
  name          = "${var.name_prefix}-forecast-results"
  location      = var.region
  force_destroy = false

  lifecycle_rule {
    condition {
      age = var.results_retention_days
    }
    action {
      type = "Delete"
    }
  }

  uniform_bucket_level_access = true
}

# --- Persistent Disk ---
resource "google_compute_disk" "timescaledb" {
  name = "${var.name_prefix}-timescaledb"
  type = "pd-ssd"
  zone = var.zone
  size = var.db_volume_size_gb

  labels = merge(var.tags, {
    persistent = "true"
  })
}

# --- Artifact Registry ---
resource "google_artifact_registry_repository" "backend" {
  location      = var.region
  repository_id = "${var.name_prefix}-backend"
  format        = "DOCKER"
}

resource "google_artifact_registry_repository" "analytics" {
  location      = var.region
  repository_id = "${var.name_prefix}-analytics"
  format        = "DOCKER"
}

# --- Service Account ---
resource "google_service_account" "forecast" {
  account_id   = "${var.name_prefix}-forecast"
  display_name = "Tickonomics Forecast Spot VM"
}

resource "google_project_iam_member" "storage_write" {
  project = var.gcp_project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.forecast.email}"
}

resource "google_project_iam_member" "artifact_read" {
  project = var.gcp_project_id
  role    = "roles/artifactregistry.reader"
  member  = "serviceAccount:${google_service_account.forecast.email}"
}

resource "google_project_iam_member" "logging" {
  project = var.gcp_project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.forecast.email}"
}

# --- Preemptible VM ---
data "google_compute_image" "ubuntu" {
  family  = "ubuntu-2204-lts"
  project = "ubuntu-os-cloud"
}

resource "google_compute_instance" "forecast" {
  name         = "${var.name_prefix}-forecast-spot"
  zone         = var.zone
  machine_type = var.instance_type

  scheduling {
    preemptible       = true
    automatic_restart = false
  }

  boot_disk {
    initialize_params {
      image = data.google_compute_image.ubuntu.self_link
      size  = 30
      type  = "pd-ssd"
    }
  }

  attached_disk {
    source      = google_compute_disk.timescaledb.id
    device_name = "timescaledb"
  }

  network_interface {
    subnetwork = google_compute_subnetwork.public.id
    access_config {}
  }

  metadata = {
    ssh-keys = "ubuntu:${var.ssh_public_key}"
  }

  metadata_startup_script = file(
    "${path.module}/../../shared/scripts/forecast-task.sh"
  )

  service_account {
    email  = google_service_account.forecast.email
    scopes = ["cloud-platform"]
  }

  labels = merge(var.tags, {
    component = "forecast"
  })
}

# --- Cloud Function: Forecast Trigger ---
resource "google_storage_bucket_object" "trigger_source" {
  name   = "forecast-trigger-source.zip"
  bucket = google_storage_bucket.results.name
  source = data.archive_file.gcp_trigger.output_path
}

data "archive_file" "gcp_trigger" {
  type        = "zip"
  output_path = "${path.module}/dist/forecast_trigger.zip"
  source {
    content  = file("${path.module}/../../modules/orchestrator/src/forecast_trigger.py")
    filename = "main.py"
  }
}

resource "google_cloudfunctions2_function" "trigger" {
  name        = "${var.name_prefix}-forecast-trigger"
  location    = var.region
  description = "Triggers forecast spot instance"

  build_config {
    runtime     = "python312"
    entry_point = "handler"
    source {
      storage_source {
        bucket = google_storage_bucket.results.name
        object = google_storage_bucket_object.trigger_source.name
      }
    }
  }

  service_config {
    environment_variables = {
      RESULTS_BUCKET = google_storage_bucket.results.name
    }
  }
}

# --- Cloud Scheduler ---
resource "google_cloud_scheduler_job" "forecast" {
  name             = "${var.name_prefix}-forecast-schedule"
  region           = var.region
  schedule         = var.schedule_expression
  time_zone        = "UTC"
  attempt_deadline = "300s"

  http_target {
    uri         = google_cloudfunctions2_function.trigger.url
    http_method = "POST"
    oidc_token {
      service_account_email = google_service_account.forecast.email
    }
  }
}

# --- Pub/Sub: Preemption Notification ---
resource "google_pubsub_topic" "preemption" {
  name = "${var.name_prefix}-preemption"
}

resource "google_logging_metric" "preemption" {
  name   = "${var.name_prefix}-vm-preemption"
  filter = "resource.type=\"gce_instance\" jsonPayload.event_subtype=\"compute.instances.preempted\""

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
  }
}

# --- Cloud Function HTTP endpoint (replaces API Gateway) ---
# NOTE: Google API Gateway resources (google_api_gateway_api, etc.) are not
# available in the hashicorp/google provider. The Cloud Function URL serves
# as the HTTP trigger endpoint directly. Add a Cloud Endpoints / API Gateway
# via gcloud CLI if a custom domain or OpenAPI validation is needed.
