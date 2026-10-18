# aws-forecast: the standalone one-shot forecast stack.
# Owns the shared base (networking/storage/container_registry) plus the ephemeral
# spot path (compute_spot/orchestrator/monitoring). No always-on EC2 — applying this
# runs a forecast and self-terminates. aws-production consumes the base via remote_state.

module "networking" {
  source = "../../modules/networking"

  availability_zone = var.availability_zone
  name_prefix       = var.name_prefix
  tags              = var.tags
}

module "storage" {
  source = "../../modules/storage"

  region                 = var.region
  availability_zone      = var.availability_zone
  db_volume_size_gb      = var.db_volume_size_gb
  results_retention_days = var.results_retention_days
  name_prefix            = var.name_prefix
  tags                   = var.tags
}

module "container_registry" {
  source = "../../modules/container-registry"

  name_prefix = var.name_prefix
  tags        = var.tags
}

module "compute_spot" {
  source = "../../modules/compute-spot"

  region                = var.region
  availability_zone     = var.availability_zone
  instance_type         = var.instance_type
  spot_price_max        = var.spot_price_max
  subnet_id             = module.networking.public_subnet_id
  security_group_ids    = [module.networking.spot_security_group_id]
  ebs_volume_id         = module.storage.ebs_volume_id
  registry_url          = split("/", module.container_registry.backend_repository_url)[0]
  backend_image         = "${module.container_registry.backend_repository_url}:${var.image_tag}"
  analytics_image       = "${module.container_registry.analytics_repository_url}:${var.image_tag}"
  postgres_password     = var.postgres_password
  finnhub_api_key       = var.finnhub_api_key
  alphavantage_api_key  = var.alphavantage_api_key
  fred_api_key          = var.fred_api_key
  results_bucket        = module.storage.results_bucket_name
  auto_terminate        = var.auto_terminate
  s3_write_policy_arn   = module.storage.s3_write_policy_arn
  ebs_attach_policy_arn = module.storage.ebs_attach_policy_arn
  name_prefix           = var.name_prefix
  tags                  = var.tags

  depends_on = [module.storage, module.networking]
}

module "orchestrator" {
  source = "../../modules/orchestrator"

  region              = var.region
  subnet_id           = module.networking.public_subnet_id
  security_group_ids  = [module.networking.spot_security_group_id]
  launch_template_id  = module.compute_spot.launch_template_id
  spot_price_max      = var.spot_price_max
  results_bucket_name = module.storage.results_bucket_name
  results_bucket_arn  = module.storage.results_bucket_arn
  schedule_expression = var.schedule_expression
  name_prefix         = var.name_prefix
  tags                = var.tags

  depends_on = [module.compute_spot]
}

module "monitoring" {
  source = "../../modules/monitoring"

  region      = var.region
  alert_email = var.alert_email
  log_group_names = [
    "/aws/lambda/${var.name_prefix}-forecast-trigger",
    "/aws/lambda/${var.name_prefix}-forecast-status",
  ]
  name_prefix = var.name_prefix
  tags        = var.tags
}

# Operator access: an assumable role for AssumeRole chaining. The AWS CLI/SDK re-assumes it
# automatically when temporary credentials expire — no credential_process or Identity Center
# token cache involved. See the runbook for the matching ~/.aws/config profile.
module "admin_role" {
  source = "../../modules/admin-role"

  name_prefix              = var.name_prefix
  tags                     = var.tags
  trust_principals         = var.admin_trust_principals
  session_duration_seconds = var.admin_session_duration_seconds
}
