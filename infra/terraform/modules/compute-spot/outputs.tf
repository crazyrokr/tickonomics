output "instance_public_ip" {
  description = "Public IP address of the spot instance"
  value       = aws_spot_instance_request.forecast.public_ip
}

output "instance_id" {
  description = "Spot instance ID"
  value       = aws_spot_instance_request.forecast.spot_instance_id
}

output "spot_request_id" {
  description = "Spot request ID"
  value       = aws_spot_instance_request.forecast.id
}
