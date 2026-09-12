#!/usr/bin/env bash
# Called by the locked artifact gate, directly or in isolated pipeline shells.

release_step() {
  if [[ -n "${release_cancel_file:-}" && -e "$release_cancel_file" ]]; then
    echo 'Experiment cancelled; active stages finished and no further stages will start.' >&2
    return 125
  fi
  check_run "$@"
}

release_frontend() {
  cd "$release_source_worktree"
  release_step frontend-install yarn install --frozen-lockfile
  release_step frontend-lint yarn lint
  if [[ "$release_mode" == parallel-browser || "$release_mode" == combined ]]; then
    release_step browser-tests yarn test:e2e --config playwright.experiment.config.js
  else
    release_step browser-tests yarn test:e2e
  fi
  release_step pwa-upgrade-tests yarn test:pwa
  release_step frontend-production-build yarn build
}

release_backend() {
  cd "$release_source_worktree/backend"
  release_step backend-tests ./gradlew test
  release_step backend-production-build ./gradlew bootJar
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  release_source_worktree="$1"
  release_mode="$4"
  release_cancel_file="$3"
  source "$release_source_worktree/scripts/lib/checks.sh"
  check_init "$release_source_worktree"
  "release_$2"
fi
