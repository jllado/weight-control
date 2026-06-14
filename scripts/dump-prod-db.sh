#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $# -lt 1 || $# -gt 3 ]]; then
  echo "Usage: $0 <ssh-host> [remote-app-dir] [local-output-path]" >&2
  echo "Example: $0 deploy@weightcontrol.devjllado.com /opt/weight-control tmp/prod-$(date +%F).sql.gz" >&2
  exit 1
fi

SSH_HOST="$1"
REMOTE_APP_DIR="${2:-/opt/weight-control}"
LOCAL_OUTPUT_PATH="${3:-$ROOT_DIR/tmp/prod-$(date +%F-%H%M%S).sql.gz}"

mkdir -p "$(dirname "$LOCAL_OUTPUT_PATH")"

echo "Dumping production database from $SSH_HOST:$REMOTE_APP_DIR ..."

ssh "$SSH_HOST" "cd '$REMOTE_APP_DIR' && set -a && source .env && set +a && docker compose exec -T mariadb mariadb-dump -u\"\$DB_USER\" -p\"\$DB_PASSWORD\" --single-transaction --routines --triggers \"\$DB_NAME\" | gzip -c" > "$LOCAL_OUTPUT_PATH"

echo "Dump saved to $LOCAL_OUTPUT_PATH"
