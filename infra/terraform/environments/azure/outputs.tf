output "vm_public_ip" {
  description = "Public IP of the spot VM"
  value       = azurerm_linux_virtual_machine.forecast.public_ip_address
}

output "results_container_name" {
  description = "Blob container where forecast results are uploaded"
  value       = azurerm_storage_container.results.name
}

output "acr_login_server" {
  description = "Azure Container Registry login server"
  value       = azurerm_container_registry.acr.login_server
}

output "managed_disk_id" {
  description = "Managed disk ID (survives VM deallocation)"
  value       = azurerm_managed_disk.timescaledb.id
}

output "function_app_endpoint" {
  description = "Azure Function App default hostname"
  value       = azurerm_linux_function_app.forecast.default_hostname
}
