#!/usr/bin/env bash
set -euo pipefail

check_worktree="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$check_worktree/scripts/lib/checks.sh"
check_acquire_lock validation "$check_worktree"
check_init "$check_worktree"
check_target="${1:?Usage: scripts/check.sh <frontend|backend|scripts> <tool arguments...>}"
shift
case "$check_target" in
  frontend) cd "$check_worktree"; check_run frontend yarn "$@" ;;
  backend) cd "$check_worktree/backend"; check_run backend ./gradlew "$@" ;;
  scripts) cd "$check_worktree"; check_run release-scripts python3 -B -m unittest discover -s tests/scripts -v ;;
  *) echo "Expected frontend, backend, or scripts." >&2; exit 2 ;;
esac
