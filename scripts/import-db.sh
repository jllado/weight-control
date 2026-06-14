#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Resetting Docker volumes..."
docker compose down -v

echo "Starting containers..."
docker compose up --build -d

echo "Waiting for backend import to finish..."
while true; do
  BACKEND_LOGS="$(docker compose logs --no-color backend 2>&1 || true)"
  BACKEND_STATUS="$(docker compose ps --format json backend 2>/dev/null || true)"

  if grep -q "Bootstrap import completed" <<<"$BACKEND_LOGS"; then
    break
  fi

  if grep -q '"State":"exited"' <<<"$BACKEND_STATUS"; then
    echo "Backend import failed. Recent backend logs:" >&2
    docker compose logs --no-color --tail=200 backend >&2
    exit 1
  fi

  sleep 2
done

echo "Import finished. Current row counts:"
docker compose exec -T mariadb mariadb -uweight_control -pweight_control -e '
select count(*) as users from users;
select count(*) as weights from weights;
select count(*) as blood_pressures from blood_pressures;
select count(*) as habits from habits;
select count(*) as routines from routines;
select count(*) as routine_checkins from routine_checkins;
select count(*) as daily_statuses from daily_statuses;
select dashboard_anchor_date from users;
' weight_control
