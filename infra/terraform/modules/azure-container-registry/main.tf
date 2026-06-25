resource "azurerm_container_registry" "acr" {
  name                = "${replace(var.name_prefix, "-", "")}registry"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "Basic"
  admin_enabled       = true

  tags = var.tags
}
