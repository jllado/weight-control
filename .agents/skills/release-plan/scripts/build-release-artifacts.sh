#!/usr/bin/env bash

set -euo pipefail

release_source_worktree="$(cd "${1:?Usage: $0 <source-worktree>}" && pwd)"
release_master_worktree="$(
  git -C "$release_source_worktree" worktree list --porcelain | awk '
    /^worktree / { worktree = substr($0, 10) }
    $0 == "branch refs/heads/master" { print worktree; exit }
  '
)"
source "$release_source_worktree/scripts/lib/checks.sh"
check_acquire_lock validation "$release_source_worktree"
check_init "$release_source_worktree"
release_manifest_dir="$release_source_worktree/tmp/release-artifacts"
mkdir -p "$release_manifest_dir"
# The tree file is the readiness marker; failed runs must not leave old artifacts ready.
check_ready_marker="$release_manifest_dir/tree"
rm -f "$check_ready_marker"
release_candidate_tree="$(git -C "$release_source_worktree" rev-parse 'HEAD^{tree}')"
release_env_file="$release_master_worktree/.env"
release_google_client_id="$(sed -n 's/^VUE_APP_GOOGLE_CLIENT_ID=//p' "$release_env_file")"
release_chatgpt_coach_url="$(sed -n 's/^VUE_APP_CHATGPT_COACH_URL=//p' "$release_env_file")"

if [[ -n "$(git -C "$release_source_worktree" status --porcelain)" ]]; then
  echo "Release artifacts require a clean source worktree: $release_source_worktree" >&2
  exit 1
fi

if [[ -z "$release_google_client_id" ]]; then
  echo "VUE_APP_GOOGLE_CLIENT_ID is missing from $release_env_file." >&2
  exit 1
fi

if [[ -z "$release_chatgpt_coach_url" ]]; then
  echo "VUE_APP_CHATGPT_COACH_URL is missing from $release_env_file." >&2
  exit 1
fi

export VUE_APP_GOOGLE_CLIENT_ID="$release_google_client_id"
export VUE_APP_CHATGPT_COACH_URL="$release_chatgpt_coach_url"

echo "Building release artifacts from $(git -C "$release_source_worktree" rev-parse --short HEAD)..."
cd "$release_source_worktree"
check_run release-scripts python3 -B -m unittest discover -s tests/scripts -v
check_run frontend-install yarn install --frozen-lockfile
check_run frontend-lint yarn lint
check_run browser-tests yarn test:e2e
check_run frontend-production-build yarn build
cd "$release_source_worktree/backend"
check_run backend-tests ./gradlew test
check_run backend-production-build ./gradlew bootJar

if [[ -n "$(git -C "$release_source_worktree" status --porcelain)" || "$(git -C "$release_source_worktree" rev-parse 'HEAD^{tree}')" != "$release_candidate_tree" ]]; then
  echo "Release validation changed the source worktree: $release_source_worktree" >&2
  exit 1
fi

mapfile -t release_jars < <(find "$release_source_worktree/backend/build/libs" -maxdepth 1 -type f -name '*.jar' -print)
if [[ "${#release_jars[@]}" -ne 1 ]]; then
  echo "Expected exactly one backend release JAR, found ${#release_jars[@]}." >&2
  exit 1
fi

(
  cd "$release_source_worktree"
  find dist -type f -print0 | sort -z | xargs -0 sha256sum > "$release_manifest_dir/frontend.sha256"
  sha256sum "backend/build/libs/$(basename "${release_jars[0]}")" > "$release_manifest_dir/backend.sha256"
)

printf '%s\n' "$release_candidate_tree" > "$release_manifest_dir/tree.pending"
mv "$release_manifest_dir/tree.pending" "$release_manifest_dir/tree"
echo "Release artifacts are ready."
