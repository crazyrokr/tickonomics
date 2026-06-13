output "api_endpoint" {
  description = "API Gateway endpoint URL"
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "trigger_function_name" {
  description = "Name of the forecast trigger Lambda function"
  value       = aws_lambda_function.forecast_trigger.function_name
}

output "status_function_name" {
  description = "Name of the forecast status Lambda function"
  value       = aws_lambda_function.forecast_status.function_name
}
