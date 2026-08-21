#!/usr/bin/env bash

set -euo pipefail

release_master_worktree="$(
  git worktree list --porcelain | awk '
    /^worktree / { worktree = substr($0, 10) }
    $0 == "branch refs/heads/master" { print worktree; exit }
  '
)"
release_commit_sha="$(git -C "$release_master_worktree" rev-parse --verify "${1:?Usage: $0 <feature-commit>}^{commit}")"
release_feature_name="$(git -C "$release_master_worktree" show --no-patch --format=%s "$release_commit_sha")"
release_feature_name="$(node -e '
  const characters = Array.from(process.argv[1]);
  process.stdout.write(characters.length <= 80 ? process.argv[1] : `${characters.slice(0, 79).join("")}…`);
' "$release_feature_name")"
release_notification_payload="$(node -e '
  process.stdout.write(JSON.stringify({commitSha: process.argv[1], featureName: process.argv[2]}));
' "$release_commit_sha" "$release_feature_name")"
release_process_pattern='[/]ansible-playbook .*infra/ansible/deploy-app[.]yml'
release_frontend_url='https://weightcontrol.devjllado.com/'
release_backend_url='https://weightcontrol.devjllado.com/api/auth/me'
release_service_worker_url='https://weightcontrol.devjllado.com/service-worker.js'
release_push_worker_url='https://weightcontrol.devjllado.com/push-service-worker.js'
release_notification_url='https://weightcontrol.devjllado.com/api/push/release-notification'
release_env_file="$release_master_worktree/.env"
release_chatgpt_action_token="$(sed -n 's/^CHATGPT_ACTION_TOKEN=//p' "$release_env_file")"
release_chatgpt_coach_url="$(sed -n 's/^VUE_APP_CHATGPT_COACH_URL=//p' "$release_env_file")"
release_vapid_public_key="$(sed -n 's/^APP_VAPID_PUBLIC_KEY=//p' "$release_env_file")"
release_vapid_private_key="$(sed -n 's/^APP_VAPID_PRIVATE_KEY=//p' "$release_env_file")"
release_push_release_token="$(sed -n 's/^APP_PUSH_RELEASE_TOKEN=//p' "$release_env_file")"
release_mailgun_smtp_password="$(sed -n 's/^MAILGUN_SMTP_PASSWORD=//p' "$release_env_file")"

if [[ -z "$release_vapid_public_key" && -z "$release_vapid_private_key" ]]; then
  read -r release_vapid_public_key release_vapid_private_key < <(node -e '
    const {generateKeyPairSync} = require("node:crypto");
    const {publicKey, privateKey} = generateKeyPairSync("ec", {
      namedCurve: "prime256v1",
      publicKeyEncoding: {format: "jwk"},
      privateKeyEncoding: {format: "jwk"}
    });
    const publicBytes = Buffer.concat([Buffer.from([4]), Buffer.from(publicKey.x, "base64url"), Buffer.from(publicKey.y, "base64url")]);
    process.stdout.write(`${publicBytes.toString("base64url")} ${privateKey.d}\n`);
  ')
  printf '\nAPP_VAPID_PUBLIC_KEY=%s\nAPP_VAPID_PRIVATE_KEY=%s\n' "$release_vapid_public_key" "$release_vapid_private_key" >> "$release_env_file"
fi

if [[ -z "$release_push_release_token" ]]; then
  release_push_release_token="$(node -e 'process.stdout.write(require("node:crypto").randomBytes(32).toString("base64url"))')"
  printf '\nAPP_PUSH_RELEASE_TOKEN=%s\n' "$release_push_release_token" >> "$release_env_file"
fi

if [[ -z "$release_chatgpt_action_token" ]]; then
  echo "CHATGPT_ACTION_TOKEN is missing from $release_env_file." >&2
  exit 1
fi

if [[ -z "$release_chatgpt_coach_url" ]]; then
  echo "VUE_APP_CHATGPT_COACH_URL is missing from $release_env_file." >&2
  exit 1
fi

if [[ -z "$release_vapid_public_key" || -z "$release_vapid_private_key" ]]; then
  echo "Both APP_VAPID_PUBLIC_KEY and APP_VAPID_PRIVATE_KEY are required in $release_env_file." >&2
  exit 1
fi

if [[ -z "$release_push_release_token" ]]; then
  echo "APP_PUSH_RELEASE_TOKEN is missing from $release_env_file." >&2
  exit 1
fi

if [[ -z "$release_mailgun_smtp_password" ]]; then
  echo "MAILGUN_SMTP_PASSWORD is missing from $release_env_file." >&2
  exit 1
fi

export CHATGPT_ACTION_TOKEN="$release_chatgpt_action_token"
export VUE_APP_CHATGPT_COACH_URL="$release_chatgpt_coach_url"
export APP_VAPID_PUBLIC_KEY="$release_vapid_public_key"
export APP_VAPID_PRIVATE_KEY="$release_vapid_private_key"
export APP_PUSH_RELEASE_TOKEN="$release_push_release_token"
export MAILGUN_SMTP_PASSWORD="$release_mailgun_smtp_password"

while pgrep -f "$release_process_pattern" > /dev/null; do
  echo "A production deployment is in progress; waiting 15 seconds..."
  sleep 15
done

echo "Deploying feature: $release_feature_name ($release_commit_sha)"
cd "$release_master_worktree"
"$release_master_worktree/.venv-ansible/bin/ansible-playbook" \
  -i "$release_master_worktree/infra/ansible/inventory.ini" \
  "$release_master_worktree/infra/ansible/deploy-app.yml"

release_deadline=$((SECONDS + 120))
while (( SECONDS < release_deadline )); do
  release_frontend_status="$(curl --silent --location --output /dev/null --write-out '%{http_code}' --max-time 5 "$release_frontend_url" || true)"
  release_backend_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "$release_backend_url" || true)"
  release_service_worker="$(curl --silent --fail --max-time 5 "$release_service_worker_url" || true)"
  release_push_worker="$(curl --silent --fail --max-time 5 "$release_push_worker_url" || true)"
  if [[ "$release_frontend_status" == "200" && "$release_backend_status" == "403" && "$release_service_worker" == *push-service-worker.js* && "$release_push_worker" == *"addEventListener('push'"* && "$release_push_worker" == *"addEventListener('notificationclick'"* ]]; then
    release_notification_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 30 --request POST --header "Authorization: Bearer $release_push_release_token" --header 'Content-Type: application/json' --data "$release_notification_payload" "$release_notification_url" || true)"
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
