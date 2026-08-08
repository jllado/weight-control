#!/usr/bin/env bash

set -euo pipefail

release_master_worktree="$(
  git worktree list --porcelain | awk '
    /^worktree / { worktree = substr($0, 10) }
    $0 == "branch refs/heads/master" { print worktree; exit }
  '
)"
release_process_pattern='[/]ansible-playbook .*infra/ansible/deploy-app[.]yml'
release_frontend_url='https://weightcontrol.devjllado.com/'
release_backend_url='https://weightcontrol.devjllado.com/api/auth/me'
release_env_file="$release_master_worktree/.env"
release_chatgpt_action_token="$(sed -n 's/^CHATGPT_ACTION_TOKEN=//p' "$release_env_file")"
release_chatgpt_reflection_url="$(sed -n 's/^VUE_APP_CHATGPT_REFLECTION_URL=//p' "$release_env_file")"
release_vapid_public_key="$(sed -n 's/^APP_VAPID_PUBLIC_KEY=//p' "$release_env_file")"
release_vapid_private_key="$(sed -n 's/^APP_VAPID_PRIVATE_KEY=//p' "$release_env_file")"

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

if [[ -z "$release_chatgpt_action_token" ]]; then
  echo "CHATGPT_ACTION_TOKEN is missing from $release_env_file." >&2
  exit 1
fi

if [[ -z "$release_chatgpt_reflection_url" ]]; then
  echo "VUE_APP_CHATGPT_REFLECTION_URL is missing from $release_env_file." >&2
  exit 1
fi

if [[ -z "$release_vapid_public_key" || -z "$release_vapid_private_key" ]]; then
  echo "Both APP_VAPID_PUBLIC_KEY and APP_VAPID_PRIVATE_KEY are required in $release_env_file." >&2
  exit 1
fi

export CHATGPT_ACTION_TOKEN="$release_chatgpt_action_token"
export VUE_APP_CHATGPT_REFLECTION_URL="$release_chatgpt_reflection_url"
export APP_VAPID_PUBLIC_KEY="$release_vapid_public_key"
export APP_VAPID_PRIVATE_KEY="$release_vapid_private_key"

while pgrep -f "$release_process_pattern" > /dev/null; do
  echo "A production deployment is in progress; waiting 15 seconds..."
  sleep 15
done

cd "$release_master_worktree"
"$release_master_worktree/.venv-ansible/bin/ansible-playbook" \
  -i "$release_master_worktree/infra/ansible/inventory.ini" \
  "$release_master_worktree/infra/ansible/deploy-app.yml"

release_deadline=$((SECONDS + 120))
while (( SECONDS < release_deadline )); do
  release_frontend_status="$(curl --silent --location --output /dev/null --write-out '%{http_code}' --max-time 5 "$release_frontend_url" || true)"
  release_backend_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "$release_backend_url" || true)"
  if [[ "$release_frontend_status" == "200" && "$release_backend_status" == "403" ]]; then
    echo "Production verification succeeded with frontend HTTP 200 and backend HTTP 403."
    exit 0
  fi
  echo "Production returned frontend HTTP ${release_frontend_status:-000} and backend HTTP ${release_backend_status:-000}; retrying in 5 seconds..."
  sleep 5
done

echo "Production frontend and backend did not become ready within two minutes." >&2
exit 1
