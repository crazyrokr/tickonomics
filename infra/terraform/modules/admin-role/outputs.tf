output "admin_role_arn" {
  description = "ARN of the assumable admin role — use as role_arn in your AWS CLI/SDK profile"
  value       = aws_iam_role.admin.arn
}

output "admin_role_name" {
  description = "Name of the assumable admin role"
  value       = aws_iam_role.admin.name
}
