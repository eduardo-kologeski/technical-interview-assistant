#!/usr/bin/env sh
set -eu

test_directory="$(mktemp -d)"
trap 'rm -rf "$test_directory"' EXIT
backup_file="$test_directory/corrupted.dump"
printf '%s' 'corrupted-backup' > "$backup_file"
printf '%064d  %s\n' 0 "$(basename "$backup_file")" > "$backup_file.sha256"

set +e
output="$(ALLOW_RESTORE=true RESTORE_DATABASE_URL=postgresql://unused \
  "$(dirname "$0")/../restore-postgres.sh" "$backup_file" 2>&1)"
status=$?
set -e

[ "$status" -ne 0 ] || { printf '%s\n' 'Restore unexpectedly accepted a corrupted backup.' >&2; exit 1; }
printf '%s' "$output" | grep -q 'checksum verification failed' || {
  printf 'Unexpected restore error: %s\n' "$output" >&2
  exit 1
}
