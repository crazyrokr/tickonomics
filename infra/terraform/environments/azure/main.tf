# --- Resource Group ---
resource "azurerm_resource_group" "main" {
  name     = "${var.name_prefix}-forecast-rg"
  location = var.region

  tags = var.tags
}

# --- Networking ---
resource "azurerm_virtual_network" "main" {
  name                = "${var.name_prefix}-vnet"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  address_space       = ["10.0.0.0/16"]

  tags = var.tags
}

resource "azurerm_subnet" "public" {
  name                 = "${var.name_prefix}-public-subnet"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.1.0/24"]
}

resource "azurerm_network_security_group" "spot" {
  name                = "${var.name_prefix}-spot-nsg"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location

  security_rule {
    name                       = "SSH"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "AllowOutbound"
    priority                   = 200
    direction                  = "Outbound"
    access                     = "Allow"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  tags = var.tags
}

resource "azurerm_subnet_network_security_group_association" "public" {
  subnet_id                 = azurerm_subnet.public.id
  network_security_group_id = azurerm_network_security_group.spot.id
}

# --- Storage ---
resource "azurerm_storage_account" "results" {
  name                     = "${replace(var.name_prefix, "-", "")}forecast"
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  blob_properties {
    versioning_enabled = false
  }

  tags = var.tags
}

resource "azurerm_storage_container" "results" {
  name                  = "forecast-results"
  storage_account_name  = azurerm_storage_account.results.name
  container_access_type = "private"
}

resource "azurerm_storage_management_policy" "lifecycle" {
  storage_account_id = azurerm_storage_account.results.id

  rule {
    name    = "expire-results"
    enabled = true

    filters {
      blob_types   = ["blockBlob"]
      prefix_match = ["forecast-results/"]
    }

    actions {
      base_blob {
        delete_after_days_since_modification_greater_than = var.results_retention_days
      }
    }
  }
}

# --- Managed Disk ---
resource "azurerm_managed_disk" "timescaledb" {
  name                 = "${var.name_prefix}-timescaledb-disk"
  location             = azurerm_resource_group.main.location
  resource_group_name  = azurerm_resource_group.main.name
  storage_account_type = "Premium_LRS"
  create_option        = "Empty"
  disk_size_gb         = var.db_volume_size_gb
  zone                 = var.availability_zone

  tags = merge(var.tags, {
    Persistent = "true"
  })
}

# --- Container Registry ---
resource "azurerm_container_registry" "acr" {
  name                = "${replace(var.name_prefix, "-", "")}registry"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Basic"
  admin_enabled       = true

  tags = var.tags
}

# --- Spot VM ---
resource "azurerm_public_ip" "spot" {
  name                = "${var.name_prefix}-spot-pip"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  allocation_method   = "Static"
  sku                 = "Standard"

  tags = var.tags
}

resource "azurerm_network_interface" "spot" {
  name                = "${var.name_prefix}-spot-nic"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.public.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.spot.id
  }

  tags = var.tags
}

resource "azurerm_linux_virtual_machine" "forecast" {
  name                = "${var.name_prefix}-forecast-spot"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  size                = var.instance_type
  zone                = var.availability_zone

  priority        = "Spot"
  eviction_policy = "Deallocate"
  max_bid_price   = try(tonumber(var.spot_price_max), null)

  admin_username = "azureuser"

  admin_ssh_key {
    username   = "azureuser"
    public_key = var.ssh_public_key
  }

  network_interface_ids = [azurerm_network_interface.spot.id]

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 30
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts"
    version   = "latest"
  }

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

resource "azurerm_virtual_machine_data_disk_attachment" "timescaledb" {
  managed_disk_id    = azurerm_managed_disk.timescaledb.id
  virtual_machine_id = azurerm_linux_virtual_machine.forecast.id
  lun                = 0
  caching            = "ReadWrite"
}

# --- VM Extension: Run forecast task ---
resource "azurerm_virtual_machine_extension" "forecast" {
  name                 = "${var.name_prefix}-forecast-script"
  virtual_machine_id   = azurerm_linux_virtual_machine.forecast.id
  publisher            = "Microsoft.Azure.Extensions"
  type                 = "CustomScript"
  type_handler_version = "2.1"

  settings = jsonencode({
    fileUris         = []
    commandToExecute = "bash /opt/tickonomics/scripts/forecast-task.sh"
  })

  tags = var.tags
}

# --- Function App: Forecast Orchestrator ---
resource "azurerm_service_plan" "functions" {
  name                = "${var.name_prefix}-functions-plan"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  os_type             = "Linux"
  sku_name            = "Y1"

  tags = var.tags
}

resource "azurerm_storage_account" "functions" {
  name                     = "${replace(var.name_prefix, "-", "")}funcs"
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  tags = var.tags
}

resource "azurerm_linux_function_app" "forecast" {
  name                = "${var.name_prefix}-forecast-functions"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location

  service_plan_id            = azurerm_service_plan.functions.id
  storage_account_name       = azurerm_storage_account.functions.name
  storage_account_access_key = azurerm_storage_account.functions.primary_access_key

  identity {
    type = "SystemAssigned"
  }

  site_config {
    application_stack {
      python_version = "3.12"
    }
    cors {
      allowed_origins = ["*"]
    }
  }

  app_settings = {
    RESULTS_CONTAINER        = azurerm_storage_container.results.name
    STORAGE_CONNECTION       = azurerm_storage_account.results.primary_connection_string
    FUNCTIONS_WORKER_RUNTIME = "python"
  }

  tags = var.tags
}

# --- Monitor Alert: Spot Eviction ---
resource "azurerm_monitor_metric_alert" "eviction" {
  name                = "${var.name_prefix}-spot-eviction"
  resource_group_name = azurerm_resource_group.main.name
  severity            = 2
  enabled             = true
  scopes              = [azurerm_linux_virtual_machine.forecast.id]
  description         = "Alert when Azure Spot VM receives eviction notice"
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Compute/virtualMachines"
    metric_name      = "VM Eviction"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 0
  }

  tags = var.tags
}

# --- Monitor Alert: High CPU ---
resource "azurerm_monitor_metric_alert" "high_cpu" {
  name                = "${var.name_prefix}-high-cpu"
  resource_group_name = azurerm_resource_group.main.name
  severity            = 3
  enabled             = true
  scopes              = [azurerm_linux_virtual_machine.forecast.id]
  description         = "Alert when CPU exceeds 90%"
  frequency           = "PT5M"
  window_size         = "PT1H"

  criteria {
    metric_namespace = "Microsoft.Compute/virtualMachines"
    metric_name      = "Percentage CPU"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 90
  }

  tags = var.tags
}
