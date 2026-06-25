resource "azurerm_monitor_metric_alert" "eviction" {
  name                = "${var.name_prefix}-spot-eviction"
  resource_group_name = var.resource_group_name
  severity            = 2
  enabled             = true
  scopes              = [var.vm_id]
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

resource "azurerm_monitor_metric_alert" "high_cpu" {
  name                = "${var.name_prefix}-high-cpu"
  resource_group_name = var.resource_group_name
  severity            = 3
  enabled             = true
  scopes              = [var.vm_id]
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
