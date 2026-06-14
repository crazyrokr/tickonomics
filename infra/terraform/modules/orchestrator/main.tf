data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_cloudwatch_log_group" "forecast_trigger" {
  name              = "/aws/lambda/${var.name_prefix}-forecast-trigger"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "forecast_status" {
  name              = "/aws/lambda/${var.name_prefix}-forecast-status"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "spot_interruption" {
  name              = "/aws/lambda/${var.name_prefix}-spot-interruption"
  retention_in_days = 14
}

data "archive_file" "forecast_trigger" {
  type        = "zip"
  output_path = "${path.module}/dist/forecast_trigger.zip"
  source {
    content  = file("${path.module}/src/forecast_trigger.py")
    filename = "forecast_trigger.py"
  }
}

data "archive_file" "forecast_status" {
  type        = "zip"
  output_path = "${path.module}/dist/forecast_status.zip"
  source {
    content  = file("${path.module}/src/forecast_status.py")
    filename = "forecast_status.py"
  }
}

data "archive_file" "spot_interruption" {
  type        = "zip"
  output_path = "${path.module}/dist/spot_interruption.zip"
  source {
    content  = file("${path.module}/src/spot_interruption.py")
    filename = "spot_interruption.py"
  }
}

resource "aws_lambda_function" "forecast_trigger" {
  filename      = data.archive_file.forecast_trigger.output_path
  function_name = "${var.name_prefix}-forecast-trigger"
  role          = aws_iam_role.lambda_trigger.arn
  handler       = "forecast_trigger.handler"
  runtime       = "python3.12"
  timeout       = 120
  memory_size   = 256

  environment {
    variables = {
      RESULTS_BUCKET     = var.results_bucket_name
      LAUNCH_TEMPLATE_ID = var.launch_template_id
      SUBNET_ID          = var.subnet_id
      SECURITY_GROUP_IDS = join(",", var.security_group_ids)
      SPOT_PRICE_MAX     = var.spot_price_max
      INSTANCE_NAME      = "${var.name_prefix}-spot-forecast"
    }
  }

  depends_on = [aws_cloudwatch_log_group.forecast_trigger]

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-forecast-trigger"
  })
}

resource "aws_lambda_function" "forecast_status" {
  filename      = data.archive_file.forecast_status.output_path
  function_name = "${var.name_prefix}-forecast-status"
  role          = aws_iam_role.lambda_status.arn
  handler       = "forecast_status.handler"
  runtime       = "python3.12"
  timeout       = 30
  memory_size   = 128

  environment {
    variables = {
      RESULTS_BUCKET = var.results_bucket_name
    }
  }

  depends_on = [aws_cloudwatch_log_group.forecast_status]

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-forecast-status"
  })
}

resource "aws_lambda_function" "spot_interruption" {
  filename      = data.archive_file.spot_interruption.output_path
  function_name = "${var.name_prefix}-spot-interruption"
  role          = aws_iam_role.lambda_interruption.arn
  handler       = "spot_interruption.handler"
  runtime       = "python3.12"
  timeout       = 30
  memory_size   = 128

  environment {
    variables = {
      RESULTS_BUCKET = var.results_bucket_name
    }
  }

  depends_on = [aws_cloudwatch_log_group.spot_interruption]

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-spot-interruption"
  })
}

resource "aws_cloudwatch_event_rule" "spot_interruption" {
  name        = "${var.name_prefix}-spot-interruption"
  description = "Detect EC2 spot instance interruption warnings"

  event_pattern = jsonencode({
    source      = ["aws.ec2"]
    detail-type = ["EC2 Spot Instance Interruption Warning"]
  })
}

resource "aws_cloudwatch_event_target" "spot_interruption" {
  rule      = aws_cloudwatch_event_rule.spot_interruption.name
  target_id = "${var.name_prefix}-spot-interruption-target"
  arn       = aws_lambda_function.spot_interruption.arn
}

resource "aws_lambda_permission" "spot_interruption" {
  statement_id  = "AllowEventBridgeInvokeInterruption"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.spot_interruption.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.spot_interruption.arn
}

resource "aws_cloudwatch_event_rule" "schedule" {
  name                = "${var.name_prefix}-forecast-schedule"
  description         = "Scheduled forecast trigger"
  schedule_expression = var.schedule_expression
  state               = "ENABLED"
}

resource "aws_cloudwatch_event_target" "schedule" {
  rule      = aws_cloudwatch_event_rule.schedule.name
  target_id = "${var.name_prefix}-forecast-schedule-target"
  arn       = aws_lambda_function.forecast_trigger.arn
}

resource "aws_lambda_permission" "schedule" {
  statement_id  = "AllowEventBridgeInvokeSchedule"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.forecast_trigger.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.schedule.arn
}

resource "aws_apigatewayv2_api" "forecast" {
  name          = "${var.name_prefix}-forecast-api"
  protocol_type = "HTTP"

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-forecast-api"
  })
}

resource "aws_apigatewayv2_integration" "trigger" {
  api_id             = aws_apigatewayv2_api.forecast.id
  integration_type   = "AWS_PROXY"
  integration_uri    = aws_lambda_function.forecast_trigger.invoke_arn
  integration_method = "POST"
}

resource "aws_apigatewayv2_integration" "status" {
  api_id             = aws_apigatewayv2_api.forecast.id
  integration_type   = "AWS_PROXY"
  integration_uri    = aws_lambda_function.forecast_status.invoke_arn
  integration_method = "POST"
}

resource "aws_apigatewayv2_route" "trigger" {
  api_id    = aws_apigatewayv2_api.forecast.id
  route_key = "POST /forecast"
  target    = "integrations/${aws_apigatewayv2_integration.trigger.id}"
}

resource "aws_apigatewayv2_route" "status" {
  api_id    = aws_apigatewayv2_api.forecast.id
  route_key = "GET /forecast/{taskId}"
  target    = "integrations/${aws_apigatewayv2_integration.status.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.forecast.id
  name        = "prod"
  auto_deploy = true

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-forecast-api-prod"
  })
}

resource "aws_lambda_permission" "api_trigger" {
  statement_id  = "AllowAPIGatewayInvokeTrigger"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.forecast_trigger.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.forecast.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_status" {
  statement_id  = "AllowAPIGatewayInvokeStatus"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.forecast_status.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.forecast.execution_arn}/*/*"
}
