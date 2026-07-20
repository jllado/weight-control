#!/usr/bin/env bash

set -euo pipefail

release_master_worktree="$(
  git worktree list --porcelain | awk '
    /^worktree / { worktree = substr($0, 10) }
    $0 == "branch refs/heads/master" { print worktree; exit }
  '
)"
release_process_pattern='[/]ansible-playbook .*infra/ansible/deploy-app[.]yml'
release_production_url='https://weightcontrol.devjllado.com/'

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
  release_http_status="$(curl --silent --location --output /dev/null --write-out '%{http_code}' --max-time 5 "$release_production_url" || true)"
  if [[ "$release_http_status" == "200" ]]; then
    echo "Production verification succeeded with HTTP 200."
    exit 0
  fi
  echo "Production returned HTTP ${release_http_status:-000}; retrying in 5 seconds..."
  sleep 5
done

echo "Production did not return HTTP 200 within two minutes." >&2
exit 1
