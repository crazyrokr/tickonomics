output "managed_disk_id" {
  description = "Managed disk ID (survives VM deallocation)"
  value       = azurerm_managed_disk.timescaledb.id
}
