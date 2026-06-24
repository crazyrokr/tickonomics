resource "azurerm_service_plan" "functions" {
  name                = "${var.name_prefix}-functions-plan"
  resource_group_name = var.resource_group_name
  location            = var.location
  os_type             = "Linux"
  sku_name            = "Y1"

  tags = var.tags
}

resource "azurerm_storage_account" "functions" {
  name                     = "${replace(var.name_prefix, "-", "")}funcs"
  resource_group_name      = var.resource_group_name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  tags = var.tags
}

resource "azurerm_linux_function_app" "forecast" {
  name                = "${var.name_prefix}-forecast-functions"
  resource_group_name = var.resource_group_name
  location            = var.location

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
    RESULTS_CONTAINER        = var.results_container_name
    STORAGE_CONNECTION       = var.results_storage_connection_string
    FUNCTIONS_WORKER_RUNTIME = "python"
  }

  tags = var.tags
}
