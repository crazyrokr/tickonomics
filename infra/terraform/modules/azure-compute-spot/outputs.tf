output "vm_id" {
  description = "ID of the spot forecast VM"
  value       = azurerm_linux_virtual_machine.forecast.id
}

output "vm_public_ip" {
  description = "Public IP of the spot VM"
  value       = azurerm_linux_virtual_machine.forecast.public_ip_address
}
