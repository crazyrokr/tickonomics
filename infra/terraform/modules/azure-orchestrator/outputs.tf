output "function_app_endpoint" {
  description = "Azure Function App default hostname"
  value       = azurerm_linux_function_app.forecast.default_hostname
}
