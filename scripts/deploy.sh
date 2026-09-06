#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
shared_dir="$project_dir/../hades-staging"
cd "$project_dir"
set -a
source ".env"
source "$shared_dir/.netdata-telegram.env"
set +a
export ANSIBLE_CONFIG="$project_dir/infra/ansible/ansible.cfg"
exec python3 "$shared_dir/scripts/notify-deploy.py" \
  --app weight-control --environment "${DEPLOY_ENVIRONMENT:-staging}" --url https://weightcontrol.devjllado.com/ -- \
  "$project_dir/.venv-ansible/bin/ansible-playbook" -i infra/ansible/inventory.ini infra/ansible/deploy-app.yml "$@"
