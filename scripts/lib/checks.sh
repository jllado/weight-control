#!/usr/bin/env bash
# Shared by validation and deployment entry points; commands stay sequential.

check_acquire_lock() {
  local kind="$1" worktree="$2" directory
  if [[ "$kind" == deployment ]]; then
    directory="$(git -C "$worktree" rev-parse --path-format=absolute --git-common-dir)"
    exec 8>"$directory/deployment.lock"
    flock -n -E 75 8 || { echo "Deployment is already running for this repository; wait for it to exit." >&2; return 75; }
  else
    directory="$(git -C "$worktree" rev-parse --absolute-git-dir)"
    exec 9>"$directory/validation.lock"
    flock -n -E 75 9 || { echo "Validation is already running in $worktree; wait for it to exit, including cleanup." >&2; return 75; }
  fi
}

check_init() {
  local worktree="$1"
  mkdir -p "$worktree/tmp/checks"
  check_log_dir="$(mktemp -d "$worktree/tmp/checks/run.XXXXXX")"
  check_pid=''
  check_ready_marker=''
  printf 'stage\tstarted_utc\tfinished_utc\tduration_seconds\texit_status\tlog\n' > "$check_log_dir/timings.tsv"
  trap 'check_interrupt 130' INT
  trap 'check_interrupt 143' TERM
  trap 'check_interrupt 129' HUP
  trap 'check_finish' EXIT
  echo "Check logs: $check_log_dir"
}

check_interrupt() {
  local status="$1"
  trap '' INT TERM HUP
  if [[ -n "$check_pid" ]]; then
    kill -TERM -- "-$check_pid" 2>/dev/null || true
    wait "$check_pid" 2>/dev/null || true
    check_wait_group
    check_record "$status"
    check_pid=''
  fi
  exit "$status"
}

check_wait_group() {
  # Wait for descendants doing cleanup; ignore exited children awaiting reaping.
  while ps -eo pgid=,stat= | awk -v group="$check_pid" '$1 == group && $2 !~ /^Z/ {found=1} END {exit !found}'; do
    sleep 0.1
  done
}

check_finish() {
  local status=$?
  if [[ "$status" != 0 && -n "$check_ready_marker" ]]; then
    rm -f "$check_ready_marker"
  fi
  echo "Check result: exit $status"
  echo "Stage timings: $check_log_dir/timings.tsv"
  cat "$check_log_dir/timings.tsv"
}

check_record() {
  local status="$1"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$check_label" "$check_started" "$(date -u +%FT%TZ)" \
    "$((SECONDS - check_seconds))" "$status" "$check_log" >> "$check_log_dir/timings.tsv"
}

check_run() {
  check_label="$1"
  shift
  check_started="$(date -u +%FT%TZ)"
  check_seconds=$SECONDS
  check_log="$check_log_dir/$check_label.log"
  echo "Starting $check_label (log: $check_log)"
  # A separate process group lets interruption target only this stage's processes.
  # Children must not inherit locks (long-lived Gradle daemons may outlive a check).
  setsid --wait "$@" 8>&- 9>&- > "$check_log" 2>&1 &
  check_pid=$!
  local status=0
  wait "$check_pid" || status=$?
  check_wait_group
  check_pid=''
  check_record "$status"
  echo "Finished $check_label: exit $status, $((SECONDS - check_seconds))s"
  return "$status"
}
