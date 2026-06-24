resource "azurerm_managed_disk" "timescaledb" {
  name                 = "${var.name_prefix}-timescaledb-disk"
  location             = var.location
  resource_group_name  = var.resource_group_name
  storage_account_type = "Premium_LRS"
  create_option        = "Empty"
  disk_size_gb         = var.db_volume_size_gb
  zone                 = var.availability_zone

  tags = merge(var.tags, {
    Persistent = "true"
  })
}
