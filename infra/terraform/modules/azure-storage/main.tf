resource "azurerm_storage_account" "results" {
  name                     = "${replace(var.name_prefix, "-", "")}forecast"
  resource_group_name      = var.resource_group_name
  location                 = var.location
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
