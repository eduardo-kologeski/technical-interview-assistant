# Multi-tenancy

## Security invariant

Tenant ownership is resolved server-side from the authenticated user and an active `CompanyMembership`. The optional `X-Company-Id` header selects only among companies where that membership is active; it never grants access by itself. Tenant-owned repository lookups combine the resource identifier with `company_id` and return a safe not-found response for cross-tenant identifiers.

## Regression matrix

`CompanyIsolationIntegrationTest` provisions Company A/User A and Company B/User B and verifies:

| Resource | Same tenant | Cross-tenant or manipulated context |
|---|---|---|
| Vacancy | list, get, search, create | get/update/deactivate by legitimate foreign UUID blocked; foreign header blocked; foreign search result absent |
| Interview | create, list, get, workspace and assessment | creation from foreign vacancy blocked; get/list/start/complete/finalize/PDF/workspace mutations blocked |
| Interviewer search | active members returned | members from another company absent |
| Invitation | inspect, accept, list, revoke, resend | foreign revoke blocked; revoked/replaced tokens invalid; token reuse blocked |
| Membership | admin list and revoke | foreign and non-admin revocation blocked; removed member loses access immediately |
| Feedback | own participant workspace | peer feedback hidden and legacy general-assessment mutation denied to interviewer |

The tests use valid UUIDs from the other tenant, direct endpoint calls and manipulated `X-Company-Id` values. Expected denial is `403` when the selected company is not a membership and `404` when a resource belongs to another valid active tenant, reducing enumeration.

## PostgreSQL and migrations

`PostgresqlFlywayMigrationTest` runs against PostgreSQL 17 through Testcontainers. It validates both an empty database and an upgrade from V11 containing legacy user, vacancy and interview rows. The upgrade must preserve those rows, backfill the legacy tenant and create the expected membership and interview participant.

CI has an explicit assertion that fails when this PostgreSQL test is missing, skipped or unsuccessful. Local execution may be skipped when Docker is unavailable; that skip is not accepted by CI.

## Logging and privacy

Tenant identifiers may appear in narrowly scoped administrative security events, but candidate content, assessment bodies, authorization headers, JWTs and invitation tokens must not be logged. Avoid tenant identifiers as unbounded metric labels.
