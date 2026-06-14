#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <dump.sql|dump.sql.gz> [compose-service-to-start]" >&2
  echo "Example: $0 tmp/prod-2026-06-14.sql.gz" >&2
  exit 1
fi

DUMP_PATH="$1"
START_TARGET="${2:-all}"

if [[ ! -f "$DUMP_PATH" ]]; then
  echo "Dump file not found: $DUMP_PATH" >&2
  exit 1
fi

set -a
source "$ROOT_DIR/.env"
set +a

echo "Resetting local Docker volumes..."
docker compose down -v

echo "Starting local MariaDB..."
docker compose up -d mariadb

echo "Waiting for MariaDB to become healthy..."
until [[ "$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q mariadb)")" == "healthy" ]]; do
  sleep 2
done

echo "Restoring database from $DUMP_PATH ..."
if [[ "$DUMP_PATH" == *.gz ]]; then
  gzip -dc "$DUMP_PATH" | docker compose exec -T mariadb mariadb -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME"
else
  docker compose exec -T mariadb mariadb -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < "$DUMP_PATH"
fi

if [[ "$START_TARGET" == "all" ]]; then
  echo "Starting full stack with import disabled..."
  APP_IMPORT_ENABLED=false docker compose up -d --build
else
  echo "Starting service $START_TARGET with import disabled..."
  APP_IMPORT_ENABLED=false docker compose up -d --build "$START_TARGET"
fi

echo "Restore finished. Current row counts:"
docker compose exec -T mariadb mariadb -u"$DB_USER" -p"$DB_PASSWORD" -e '
select count(*) as users from users;
select count(*) as weights from weights;
select count(*) as blood_pressures from blood_pressures;
select count(*) as habits from habits;
select count(*) as routines from routines;
select count(*) as routine_checkins from routine_checkins;
select count(*) as daily_statuses from daily_statuses;
select dashboard_anchor_date from users;
' "$DB_NAME"

echo
echo "Note: keep APP_IMPORT_ENABLED=false in .env for future local restarts with this restored database."
