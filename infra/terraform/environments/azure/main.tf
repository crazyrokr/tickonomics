# --- Resource Group ---
resource "azurerm_resource_group" "main" {
  name     = "${var.name_prefix}-forecast-rg"
  location = var.region

  tags = var.tags
}

module "networking" {
  source = "../../modules/azure-networking"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  name_prefix         = var.name_prefix
  tags                = var.tags
}

module "storage" {
  source = "../../modules/azure-storage"

  resource_group_name    = azurerm_resource_group.main.name
  location               = azurerm_resource_group.main.location
  results_retention_days = var.results_retention_days
  name_prefix            = var.name_prefix
  tags                   = var.tags
}

module "database" {
  source = "../../modules/azure-database"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  availability_zone   = var.availability_zone
  db_volume_size_gb   = var.db_volume_size_gb
  name_prefix         = var.name_prefix
  tags                = var.tags
}

module "container_registry" {
  source = "../../modules/azure-container-registry"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  name_prefix         = var.name_prefix
  tags                = var.tags
}

module "compute_spot" {
  source = "../../modules/azure-compute-spot"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  availability_zone   = var.availability_zone
  instance_type       = var.instance_type
  spot_price_max      = var.spot_price_max
  subnet_id           = module.networking.subnet_id
  managed_disk_id     = module.database.managed_disk_id
  ssh_public_key      = var.ssh_public_key
  name_prefix         = var.name_prefix
  tags                = var.tags

  depends_on = [module.networking, module.database]
}

module "orchestrator" {
  source = "../../modules/azure-orchestrator"

  resource_group_name               = azurerm_resource_group.main.name
  location                          = azurerm_resource_group.main.location
  results_container_name            = module.storage.results_container_name
  results_storage_connection_string = module.storage.results_storage_connection_string
  name_prefix                       = var.name_prefix
  tags                              = var.tags

  depends_on = [module.storage]
}

module "monitoring" {
  source = "../../modules/azure-monitoring"

  resource_group_name = azurerm_resource_group.main.name
  vm_id               = module.compute_spot.vm_id
  name_prefix         = var.name_prefix
  tags                = var.tags

  depends_on = [module.compute_spot]
}
