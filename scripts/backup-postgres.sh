#!/usr/bin/env sh
set -eu
umask 077

: "${DATABASE_URL:?DATABASE_URL must contain the PostgreSQL connection URI}"
output_directory="${1:-./backups}"
mkdir -p "$output_directory"
timestamp="$(date -u +%Y%m%d-%H%M%S)"
backup_file="$output_directory/interview-assistant-$timestamp.dump"

pg_dump --format=custom --no-owner --no-acl --file="$backup_file" "$DATABASE_URL"
sha256sum "$backup_file" > "$backup_file.sha256"
printf '%s\n' "$backup_file"
