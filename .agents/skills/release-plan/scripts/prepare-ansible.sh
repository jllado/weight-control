#!/usr/bin/env bash

set -euo pipefail

release_project_dir="${1:?Usage: $0 <project-dir> <requires-inventory>}"
release_requires_inventory="${2:?Usage: $0 <project-dir> <requires-inventory>}"
release_venv_dir="$release_project_dir/.venv-ansible"
release_collections_dir="$release_venv_dir/collections"
release_requirements_file="$release_project_dir/infra/ansible/requirements.yml"
release_marker_file="$release_venv_dir/.release-plan-requirements.sha256"
release_fingerprint="$(sha256sum "$release_project_dir/scripts/setup-ansible.sh" "$release_project_dir/infra/ansible/requirements.txt" "$release_requirements_file" | sha256sum | awk '{print $1}')"

if [[ ! -x "$release_venv_dir/bin/ansible-playbook" || ! -f "$release_marker_file" || "$(<"$release_marker_file")" != "$release_fingerprint" ]]; then
  echo "Preparing the pending Ansible environment..."
  "$release_project_dir/scripts/setup-ansible.sh"
  printf '%s\n' "$release_fingerprint" > "$release_marker_file"
fi

export ANSIBLE_COLLECTIONS_PATH="$release_collections_dir${ANSIBLE_COLLECTIONS_PATH:+:$ANSIBLE_COLLECTIONS_PATH}"
if ! "$release_venv_dir/bin/ansible-doc" ansible.posix.synchronize > /dev/null 2>&1; then
  echo "Installing missing Ansible collections..."
  "$release_venv_dir/bin/ansible-galaxy" collection install -r "$release_requirements_file" -p "$release_collections_dir"
fi

if [[ "$release_requires_inventory" == true ]]; then
  release_inventory_file="$release_project_dir/infra/ansible/inventory.ini"
  if [[ ! -s "$release_inventory_file" ]]; then
    release_host="${DEPLOY_HOST:-${APP_SERVER:-}}"
    release_ssh_key="${DEPLOY_SSH_KEY_PATH:-${SSH_PRIVATE_KEY_PATH:-}}"
    if [[ -z "$release_host" || -z "$release_ssh_key" ]]; then
      echo "Ansible inventory is missing. Set DEPLOY_HOST (or APP_SERVER) and DEPLOY_SSH_KEY_PATH (or SSH_PRIVATE_KEY_PATH) in the deployment environment." >&2
      exit 1
    fi
    printf '[production]\nproduction ansible_host=%s ansible_user=deploy ansible_ssh_private_key_file=%s\n' "$release_host" "$release_ssh_key" > "$release_inventory_file"
  fi
  "$release_venv_dir/bin/ansible-inventory" --inventory "$release_inventory_file" --list > /dev/null
fi
