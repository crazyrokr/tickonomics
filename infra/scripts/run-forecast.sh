#!/usr/bin/env bash
set -euo pipefail

PROVIDER=""
SYMBOL="SPY"
TAG="latest"
TF_DIR=""
RESULTS_DIR="./forecast-results"
DESTROY_AFTER=true

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Run a one-shot forecast on cloud spot instances.

Options:
  --provider aws|gcp|azure   Cloud provider (required)
  --symbol SYMBOL            Target symbol (default: SPY)
  --tag TAG                  Container image tag (default: latest)
  --results-dir DIR          Local directory for downloaded results (default: ./forecast-results)
  --no-destroy               Keep infrastructure after run
  -h, --help                 Show this help message

Examples:
  $(basename "$0") --provider aws --symbol AAPL --tag v1.2.0
  $(basename "$0") --provider gcp --no-destroy
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --provider)
      PROVIDER="$2"
      shift 2
      ;;
    --symbol)
      SYMBOL="$2"
      shift 2
      ;;
    --tag)
      TAG="$2"
      shift 2
      ;;
    --results-dir)
      RESULTS_DIR="$2"
      shift 2
      ;;
    --no-destroy)
      DESTROY_AFTER=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

if [ -z "$PROVIDER" ]; then
  echo "ERROR: --provider is required"
  usage
  exit 1
fi

case "$PROVIDER" in
  aws|gcp|azure) ;;
  *)
    echo "ERROR: Unsupported provider: $PROVIDER (use aws, gcp, or azure)"
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$PROJECT_ROOT/infra/terraform/environments/$PROVIDER"

if [ ! -d "$TF_DIR" ]; then
  echo "ERROR: Terraform environment not found at $TF_DIR"
  exit 1
fi

TASK_ID="$(date +%Y%m%d-%H%M%S)-$(openssl rand -hex 4)"

echo "========================================"
echo " Tickonomics Forecast Runner"
echo "========================================"
echo " Provider:    $PROVIDER"
echo " Symbol:      $SYMBOL"
echo " Image tag:   $TAG"
echo " Task ID:     $TASK_ID"
echo " Results dir: $RESULTS_DIR"
echo " TF dir:      $TF_DIR"
echo "========================================"
echo

cd "$TF_DIR"

echo "[1/4] Initializing Terraform..."
terraform init -upgrade

echo "[2/4] Applying compute-spot module..."
terraform apply \
  -target=module.compute_spot \
  -target=module.networking \
  -target=module.storage \
  -target=module.container_registry \
  -var="image_tag=$TAG" \
  -var="task_id=$TASK_ID" \
  -auto-approve

echo "[3/4] Waiting for instance and collecting results..."
SPOT_IP=$(terraform output -raw spot_instance_public_ip 2>/dev/null || echo "")
echo "  Instance IP: ${SPOT_IP:-N/A}"

echo "  Polling task status..."
mkdir -p "$RESULTS_DIR"

MAX_WAIT=3600
ELAPSED=0
INTERVAL=60

while [ "$ELAPSED" -lt "$MAX_WAIT" ]; do
  case "$PROVIDER" in
    aws)
      STATUS=$(aws s3 cp "s3://tickonomics-forecast-results/${TASK_ID}/STATUS" - 2>/dev/null || echo "RUNNING")
      ;;
    gcp)
      STATUS=$(gsutil cat "gs://tickonomics-forecast-results/${TASK_ID}/STATUS" 2>/dev/null || echo "RUNNING")
      ;;
    azure)
      STATUS=$(az storage blob download --container-name forecast-results --name "${TASK_ID}/STATUS" --query content --output tsv 2>/dev/null || echo "RUNNING")
      ;;
  esac

  echo "  [${ELAPSED}s/${MAX_WAIT}s] Status: $STATUS"

  if [ "$STATUS" = "COMPLETED" ]; then
    echo "  Forecast completed! Downloading results..."

    case "$PROVIDER" in
      aws)
        aws s3 sync "s3://tickonomics-forecast-results/${TASK_ID}/" "$RESULTS_DIR/$TASK_ID/"
        ;;
      gcp)
        gsutil -m cp "gs://tickonomics-forecast-results/${TASK_ID}/*" "$RESULTS_DIR/$TASK_ID/"
        ;;
      azure)
        az storage blob download-batch --destination "$RESULTS_DIR/$TASK_ID/" --source forecast-results --pattern "${TASK_ID}/*"
        ;;
    esac

    echo "  Results saved to $RESULTS_DIR/$TASK_ID/"
    break
  fi

  if [ "$STATUS" = "FAILED" ]; then
    echo "  Forecast FAILED. Check logs on the instance."
    break
  fi

  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done

if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
  echo "  WARNING: Timed out waiting for forecast to complete"
fi

if [ "$DESTROY_AFTER" = true ]; then
  echo "[4/4] Destroying spot compute resources..."
  terraform destroy \
    -target=module.compute_spot \
    -var="image_tag=$TAG" \
    -var="task_id=$TASK_ID" \
    -auto-approve
  echo "  Spot resources destroyed"
else
  echo "[4/4] Skipping destroy (--no-destroy flag set)"
fi

echo
echo "========================================"
echo " Forecast run complete: $TASK_ID"
echo "========================================"
