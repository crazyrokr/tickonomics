resource "aws_sns_topic" "alerts" {
  name = "${var.name_prefix}-forecast-alerts"

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-forecast-alerts"
  })
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_cloudwatch_metric_alarm" "spot_interruption" {
  alarm_name          = "${var.name_prefix}-spot-interruption"
  alarm_description   = "Alert when a spot instance receives an interruption notice"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "SpotInterruptionWarningCount"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Sum"
  threshold           = 1
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-spot-interruption-alarm"
  })
}

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.name_prefix}-lambda-errors"
  alarm_description   = "Alert on Lambda function errors"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 3
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = "${var.name_prefix}-forecast-trigger"
  }

  alarm_actions = [aws_sns_topic.alerts.arn]

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-lambda-errors-alarm"
  })
}

resource "aws_cloudwatch_dashboard" "forecast" {
  dashboard_name = "${var.name_prefix}-forecast"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "Spot Instance CPU (tagged forecast instances)"
          region = var.region
          metrics = [
            [{ expression = "SEARCH('{AWS/EC2,MetricName=CPUUtilization}', 'Average', 60)", id = "cpu", label = "CPUUtilization" }]
          ]
          view    = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "log"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title         = "Forecast Task Logs"
          region        = var.region
          logGroupNames = length(var.log_group_names) > 0 ? var.log_group_names : ["/aws/lambda/${var.name_prefix}-forecast-trigger"]
          view          = "table"
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "Lambda Invocations & Errors"
          region = var.region
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", "${var.name_prefix}-forecast-trigger", { stat = "Sum", color = "#2ca02c" }],
            ["AWS/Lambda", "Errors", "FunctionName", "${var.name_prefix}-forecast-trigger", { stat = "Sum", color = "#d62728" }],
            ["AWS/Lambda", "Invocations", "FunctionName", "${var.name_prefix}-forecast-status", { stat = "Sum", color = "#1f77b4" }],
            ["AWS/Lambda", "Errors", "FunctionName", "${var.name_prefix}-forecast-status", { stat = "Sum", color = "#ff7f0e" }],
          ]
          view    = "timeSeries"
          stacked = false
        }
      },
    ]
  })
}
