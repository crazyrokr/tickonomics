output "results_container_name" {
  description = "Blob container where forecast results are uploaded"
  value       = azurerm_storage_container.results.name
}

output "results_storage_account_name" {
  description = "Name of the results storage account"
  value       = azurerm_storage_account.results.name
}

output "results_storage_connection_string" {
  description = "Primary connection string of the results storage account"
  value       = azurerm_storage_account.results.primary_connection_string
  sensitive   = true
}
