resource "aws_budgets_budget" "monthly" {
  name              = "${var.name_prefix}-${var.environment_name}-budget"
  budget_type       = "COST"
  limit_amount      = tostring(var.monthly_budget_amount)
  limit_unit        = "USD"
  time_unit         = "MONTHLY"
  time_period_start = "2026-06-01_00:00"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 50
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${var.environment_name}-budget"
  })
}

resource "aws_sns_topic" "budget_alerts" {
  name = "${var.name_prefix}-${var.environment_name}-budget-alerts"

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${var.environment_name}-budget-alerts"
  })
}

output "budget_id" {
  description = "ID of the budget"
  value       = aws_budgets_budget.monthly.id
}

output "sns_topic_arn" {
  description = "ARN of the SNS topic for budget alerts"
  value       = aws_sns_topic.budget_alerts.arn
}
