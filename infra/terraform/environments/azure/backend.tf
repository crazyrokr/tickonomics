terraform {
  required_version = ">= 1.7"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }

  backend "azurerm" {
    resource_group_name  = "tickonomics-terraform"
    storage_account_name = "tickonomicstfstate"
    container_name       = "forecast-state"
    key                  = "azure/terraform.tfstate"
  }
}
