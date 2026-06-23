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
  ssh_public_key        = var.ssh_public_key
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

# --- Track 14: Production Infrastructure ---

locals {
  environment       = terraform.workspace == "production" ? "production" : "staging"
  instance_type     = terraform.workspace == "production" ? "t3.xlarge" : "t3.large"
  db_volume_size_gb = terraform.workspace == "production" ? 100 : 25
  backup_retention  = terraform.workspace == "production" ? 30 : 7
  budget_amount     = terraform.workspace == "production" ? 200 : 30
  grafana_password  = var.grafana_password
}

resource "aws_security_group" "hosting_sg" {
  name_prefix = "${var.name_prefix}-${local.environment}-hosting-"
  description = "Hosting instance SG — HTTPS only, no SSH ingress"
  vpc_id      = module.networking.vpc_id

  ingress {
    description = "HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP from anywhere (Caddy redirect to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-${local.environment}-hosting-sg"
  })
}

module "secrets" {
  source = "../../modules/secrets"

  db_password          = var.postgres_password
  finnhub_api_key      = var.finnhub_api_key
  alphavantage_api_key = var.alphavantage_api_key
  fred_api_key         = var.fred_api_key
  oauth_client_secret  = var.oauth_client_secret
  name_prefix          = var.name_prefix
  tags                 = var.tags
}

module "database" {
  source = "../../modules/database"

  availability_zone = var.availability_zone
  db_volume_size_gb = local.db_volume_size_gb
  name_prefix       = var.name_prefix
  tags              = var.tags
}

module "observability" {
  source = "../../modules/observability"

  availability_zone = var.availability_zone
  grafana_password  = local.grafana_password
  name_prefix       = var.name_prefix
  tags              = var.tags
}

module "hosting" {
  source = "../../modules/hosting"

  region               = var.region
  availability_zone    = var.availability_zone
  instance_type        = local.instance_type
  subnet_id            = module.networking.public_subnet_id
  security_group_ids   = [aws_security_group.hosting_sg.id]
  ebs_root_size_gb     = local.instance_type == "t3.xlarge" ? 100 : 50
  ebs_db_volume_id     = module.database.ebs_volume_id
  ssh_public_key       = var.ssh_public_key
  registry_url         = split("/", module.container_registry.backend_repository_url)[0]
  backend_image        = "${module.container_registry.backend_repository_url}:${var.image_tag}"
  analytics_image      = "${module.container_registry.analytics_repository_url}:${var.image_tag}"
  dashboard_image      = "${module.container_registry.dashboard_repository_url}:${var.image_tag}"
  postgres_password    = var.postgres_password
  finnhub_api_key      = var.finnhub_api_key
  alphavantage_api_key = var.alphavantage_api_key
  fred_api_key         = var.fred_api_key
  grafana_password     = local.grafana_password
  results_bucket       = module.storage.results_bucket_name
  s3_write_policy_arn  = module.storage.s3_write_policy_arn
  ssm_read_policy_arn  = module.secrets.ssm_read_policy_arn
  environment          = local.environment
  domain_name          = var.domain_name
  name_prefix          = var.name_prefix
  tags                 = var.tags

  depends_on = [module.database, module.networking, module.secrets]
}

module "backup" {
  source = "../../modules/backup"

  instance_id        = module.hosting.instance_id
  db_volume_id       = module.database.ebs_volume_id
  backup_bucket_name = "${var.name_prefix}-${local.environment}-backups"
  retention_days     = local.backup_retention
  name_prefix        = var.name_prefix
  tags               = var.tags

  depends_on = [module.hosting, module.database]
}

module "dns" {
  source = "../../modules/dns-tls"

  domain_name = var.domain_name
  api_records = local.environment == "production" ? {
    "api" = module.hosting.elastic_ip
    } : {
    "api.staging" = module.hosting.elastic_ip
  }
  app_records = local.environment == "production" ? {
    "app" = module.hosting.elastic_ip
    } : {
    "app.staging" = module.hosting.elastic_ip
  }
  landing_cname_target = var.landing_cname_target
  name_prefix          = var.name_prefix
  tags                 = var.tags

  depends_on = [module.hosting]
}

module "budget" {
  source = "../../modules/budget"

  monthly_budget_amount = local.budget_amount
  alert_email           = var.alert_email
  environment_name      = local.environment
  name_prefix           = var.name_prefix
  tags                  = var.tags
}
