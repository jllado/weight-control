#!/usr/bin/env bash
set -euo pipefail

release_frontend_url='https://weightcontrol.devjllado.com/'
release_backend_url='https://weightcontrol.devjllado.com/api/auth/me'
release_service_worker_url='https://weightcontrol.devjllado.com/service-worker.js'
release_push_worker_url='https://weightcontrol.devjllado.com/push-service-worker.js'
release_notification_url='https://weightcontrol.devjllado.com/api/push/release-notification'

release_deadline=$((SECONDS + 120))
release_verification_dir="$(mktemp -d)"
trap 'rm -rf "$release_verification_dir"' EXIT
while (( SECONDS < release_deadline )); do
  (curl --silent --location --output /dev/null --write-out '%{http_code}' --max-time 5 "$release_frontend_url" || true) > "$release_verification_dir/frontend-status" &
  (curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "$release_backend_url" || true) > "$release_verification_dir/backend-status" &
  (curl --silent --fail --max-time 5 "$release_service_worker_url" || true) > "$release_verification_dir/service-worker" &
  (curl --silent --fail --max-time 5 "$release_push_worker_url" || true) > "$release_verification_dir/push-worker" &
  wait
  release_frontend_status="$(<"$release_verification_dir/frontend-status")"
  release_backend_status="$(<"$release_verification_dir/backend-status")"
  release_service_worker="$(<"$release_verification_dir/service-worker")"
  release_push_worker="$(<"$release_verification_dir/push-worker")"
  if [[ "$release_frontend_status" == "200" && "$release_backend_status" == "403" && "$release_service_worker" == *push-service-worker.js* && "$release_push_worker" == *"addEventListener('push'"* && "$release_push_worker" == *"addEventListener('notificationclick'"* ]]; then
    release_notification_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 30 --request POST --header "Authorization: Bearer $APP_PUSH_RELEASE_TOKEN" --header 'Content-Type: application/json' --data "$RELEASE_NOTIFICATION_PAYLOAD" "$release_notification_url" || true)"
    if [[ "$release_notification_status" == "204" ]]; then
      echo "Production verification succeeded and the update notification was requested."
      exit 0
    fi
    echo "Production verification succeeded, but the update notification endpoint returned HTTP ${release_notification_status:-000}." >&2
    exit 1
  fi
  echo "Production returned frontend HTTP ${release_frontend_status:-000} and backend HTTP ${release_backend_status:-000}, but the complete app was not ready; retrying in 5 seconds..."
  sleep 5
done

echo "Production frontend and backend did not become ready within two minutes." >&2
exit 1
