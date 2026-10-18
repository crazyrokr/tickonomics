# aws-production: the always-on production stack (opt-in).
# Consumes the shared base (VPC, S3 results + IAM, ECR) from aws-forecast via
# terraform_remote_state — apply aws-forecast first. See ADR-039.

data "terraform_remote_state" "forecast" {
  backend = "s3"

  config = {
    bucket = var.state_bucket
    key    = "aws-forecast/terraform.tfstate"
    region = var.region
  }
}

locals {
  environment       = terraform.workspace == "prod" ? "prod" : "stage"
  instance_type     = terraform.workspace == "prod" ? "t4g.xlarge" : "t4g.large"
  db_volume_size_gb = terraform.workspace == "prod" ? 100 : 25
  backup_retention  = terraform.workspace == "prod" ? 30 : 7
  budget_amount     = terraform.workspace == "prod" ? 200 : 30
  grafana_password  = var.grafana_password
}

resource "aws_security_group" "hosting_sg" {
  name_prefix = "${var.name_prefix}-${local.environment}-hosting-"
  description = "Hosting instance SG — HTTPS only, no SSH ingress"
  vpc_id      = data.terraform_remote_state.forecast.outputs.vpc_id

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
  subnet_id            = data.terraform_remote_state.forecast.outputs.public_subnet_id
  security_group_ids   = [aws_security_group.hosting_sg.id]
  ebs_root_size_gb     = local.instance_type == "t4g.xlarge" ? 100 : 50
  ebs_db_volume_id     = module.database.ebs_volume_id
  ssh_public_key       = var.ssh_public_key
  registry_url         = split("/", data.terraform_remote_state.forecast.outputs.ecr_backend_url)[0]
  backend_image        = "${data.terraform_remote_state.forecast.outputs.ecr_backend_url}:${var.image_tag}"
  analytics_image      = "${data.terraform_remote_state.forecast.outputs.ecr_analytics_url}:${var.image_tag}"
  dashboard_image      = "${data.terraform_remote_state.forecast.outputs.ecr_dashboard_url}:${var.image_tag}"
  postgres_password    = var.postgres_password
  finnhub_api_key      = var.finnhub_api_key
  alphavantage_api_key = var.alphavantage_api_key
  fred_api_key         = var.fred_api_key
  grafana_password     = local.grafana_password
  results_bucket       = data.terraform_remote_state.forecast.outputs.results_bucket_name
  s3_write_policy_arn  = data.terraform_remote_state.forecast.outputs.s3_write_policy_arn
  ssm_read_policy_arn  = module.secrets.ssm_read_policy_arn
  environment          = local.environment
  domain_name          = var.domain_name
  name_prefix          = var.name_prefix
  tags                 = var.tags

  depends_on = [module.database, module.secrets]
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
  api_records = local.environment == "prod" ? {
    "api" = module.hosting.elastic_ip
    } : {
    "api.staging" = module.hosting.elastic_ip
  }
  app_records = local.environment == "prod" ? {
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
