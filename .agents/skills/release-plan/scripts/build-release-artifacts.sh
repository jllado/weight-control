#!/usr/bin/env bash

set -euo pipefail

release_source_worktree="$(cd "${1:?Usage: $0 <source-worktree>}" && pwd)"
release_master_worktree="$(
  git -C "$release_source_worktree" worktree list --porcelain | awk '
    /^worktree / { worktree = substr($0, 10) }
    $0 == "branch refs/heads/master" { print worktree; exit }
  '
)"
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
(
  cd "$release_source_worktree"
  yarn install --frozen-lockfile
  yarn lint
  yarn test:e2e
  yarn build
)
(
  cd "$release_source_worktree/backend"
  ./gradlew test
  ./gradlew bootJar
)

if [[ -n "$(git -C "$release_source_worktree" status --porcelain)" ]]; then
  echo "Release validation changed the source worktree: $release_source_worktree" >&2
  exit 1
fi

mapfile -t release_jars < <(find "$release_source_worktree/backend/build/libs" -maxdepth 1 -type f -name '*.jar' -print)
if [[ "${#release_jars[@]}" -ne 1 ]]; then
  echo "Expected exactly one backend release JAR, found ${#release_jars[@]}." >&2
  exit 1
fi

release_manifest_dir="$release_source_worktree/tmp/release-artifacts"
mkdir -p "$release_manifest_dir"
git -C "$release_source_worktree" rev-parse 'HEAD^{tree}' > "$release_manifest_dir/tree"
(
  cd "$release_source_worktree"
  find dist -type f -print0 | sort -z | xargs -0 sha256sum > "$release_manifest_dir/frontend.sha256"
  sha256sum "backend/build/libs/$(basename "${release_jars[0]}")" > "$release_manifest_dir/backend.sha256"
)

echo "Release artifacts are ready."
