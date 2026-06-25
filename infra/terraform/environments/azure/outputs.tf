output "vm_public_ip" {
  description = "Public IP of the spot VM"
  value       = module.compute_spot.vm_public_ip
}

output "results_container_name" {
  description = "Blob container where forecast results are uploaded"
  value       = module.storage.results_container_name
}

output "acr_login_server" {
  description = "Azure Container Registry login server"
  value       = module.container_registry.login_server
}

output "managed_disk_id" {
  description = "Managed disk ID (survives VM deallocation)"
  value       = module.database.managed_disk_id
}

output "function_app_endpoint" {
  description = "Azure Function App default hostname"
  value       = module.orchestrator.function_app_endpoint
}
