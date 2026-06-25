output "subnet_id" {
  description = "ID of the public subnet"
  value       = azurerm_subnet.public.id
}

output "spot_nsg_id" {
  description = "ID of the spot VM network security group"
  value       = azurerm_network_security_group.spot.id
}
