#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="$ROOT_DIR/.venv-ansible"

python3 -m venv "$VENV_DIR"
"$VENV_DIR/bin/pip" install --upgrade pip
"$VENV_DIR/bin/pip" install -r "$ROOT_DIR/infra/ansible/requirements.txt"
"$VENV_DIR/bin/ansible-galaxy" collection install -r "$ROOT_DIR/infra/ansible/requirements.yml"

cat <<EOF
Ansible virtualenv ready:
  source "$VENV_DIR/bin/activate"

Then run:
  ansible --version
  ansible -i infra/ansible/inventory.ini all -m ping
  ansible-playbook -i infra/ansible/inventory.ini infra/ansible/deploy-app.yml
EOF
