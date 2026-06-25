output "eviction_alert_name" {
  description = "Name of the spot-eviction metric alert"
  value       = azurerm_monitor_metric_alert.eviction.name
}

output "high_cpu_alert_name" {
  description = "Name of the high-CPU metric alert"
  value       = azurerm_monitor_metric_alert.high_cpu.name
}
