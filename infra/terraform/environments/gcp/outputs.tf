output "vm_public_ip" {
  description = "Public IP of the preemptible VM"
  value       = google_compute_instance.forecast.network_interface[0].access_config[0].nat_ip
}

output "results_bucket_name" {
  description = "GCS bucket where forecast results are uploaded"
  value       = google_storage_bucket.results.name
}

output "trigger_function_url" {
  description = "Cloud Function HTTP trigger URL"
  value       = google_cloudfunctions2_function.trigger.url
}

output "artifact_registry_backend" {
  description = "Artifact Registry repo for backend image"
  value       = google_artifact_registry_repository.backend.name
}

output "artifact_registry_analytics" {
  description = "Artifact Registry repo for analytics image"
  value       = google_artifact_registry_repository.analytics.name
}

output "persistent_disk_name" {
  description = "Persistent disk name (survives VM termination)"
  value       = google_compute_disk.timescaledb.name
}
