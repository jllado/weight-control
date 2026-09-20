#!/usr/bin/env bash
set -euo pipefail
release_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
exec python3 "$release_root/scripts/verify-deployment.py" "${1:?Usage: $0 <base-url> <expected-tree>}" "${2:?Usage: $0 <base-url> <expected-tree>}"
