#!/usr/bin/env sh
set -eu

: "${ALLOW_RESTORE:?Set ALLOW_RESTORE=true to acknowledge this restore operation}"
[ "$ALLOW_RESTORE" = "true" ] || { printf '%s\n' 'ALLOW_RESTORE must equal true.' >&2; exit 1; }
: "${RESTORE_DATABASE_URL:?RESTORE_DATABASE_URL must target an isolated, empty PostgreSQL database}"
: "${1:?Pass the backup file as the first argument}"
backup_file="$1"
[ -f "$backup_file" ] || { printf 'Backup not found: %s\n' "$backup_file" >&2; exit 1; }

table_count="$(psql "$RESTORE_DATABASE_URL" --tuples-only --no-align --command "SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema');")"
[ "$table_count" = "0" ] || { printf '%s\n' 'Restore refused: the target database is not empty.' >&2; exit 1; }

pg_restore --exit-on-error --no-owner --no-acl --dbname="$RESTORE_DATABASE_URL" "$backup_file"
printf '%s\n' 'Restore completed. Run Flyway and the documented integrity checks before use.'
