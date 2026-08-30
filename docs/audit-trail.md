# Tenant audit trail

Administrative and security-relevant domain events are persisted in the append-only `audit_events` table. Each
event carries its tenant, category, type, technical resource identifier, actor, correlation ID and timestamp.
Tokens, email addresses, candidate content, assessment text and report text are deliberately excluded.

Only a tenant administrator has `VIEW_AUDIT_LOG`. `GET /api/audit-events` is paginated, accepts an optional category
filter and always derives the tenant from the authenticated active membership. Selecting another tenant through
`X-Company-Id` is rejected.

The initial event set covers company creation, invitation creation/revocation, membership revocation, interview
lifecycle completion/finalization and PDF generation. Operational logs remain separate from this governance record.
Retention duration and external SIEM export remain governance/enterprise decisions.
