resource "aws_route53_zone" "primary" {
  name = var.domain_name

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-zone"
  })
}

resource "aws_route53_record" "api" {
  for_each = var.api_records

  zone_id = aws_route53_zone.primary.zone_id
  name    = each.key
  type    = "A"
  ttl     = 300
  records = [each.value]
}

resource "aws_route53_record" "app" {
  for_each = var.app_records

  zone_id = aws_route53_zone.primary.zone_id
  name    = each.key
  type    = "A"
  ttl     = 300
  records = [each.value]
}

resource "aws_route53_record" "apex_cname" {
  count = var.landing_cname_target != "" ? 1 : 0

  zone_id = aws_route53_zone.primary.zone_id
  name    = var.domain_name
  type    = "CNAME"
  ttl     = 300
  records = [var.landing_cname_target]
}

output "zone_id" {
  description = "Route53 hosted zone ID"
  value       = aws_route53_zone.primary.zone_id
}

output "name_servers" {
  description = "Route53 name servers for domain registration"
  value       = aws_route53_zone.primary.name_servers
}
