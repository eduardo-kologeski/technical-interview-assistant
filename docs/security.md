# Security

## Identity lifecycle

New accounts must verify control of their e-mail before accepting a company invitation. Verification and password-reset credentials are 256-bit random values; only SHA-256 hashes are persisted. Tokens expire, are single-use and are invalidated when a newer token of the same purpose is issued.

Password-reset requests return the same accepted response whether an account exists or not. Successful reset changes the BCrypt password hash and revokes every active refresh token for that user. Authentication and recovery endpoints are rate-limited by client address.

Delivery is provider-abstracted. `IDENTITY_EXPOSE_DEVELOPMENT_LINK` and `INVITATION_EXPOSE_DEVELOPMENT_LINK` default to `false` and must never be enabled outside local automated testing. Raw tokens, passwords, authorization headers and complete e-mail addresses must not appear in application logs.

## Authorization

Authorization is enforced server-side from the authenticated identity, active company membership and tenant-scoped resource lookup. See [multi-tenancy.md](multi-tenancy.md) for the regression matrix.

## Future controls

MFA enforcement, enterprise SSO and SCIM remain separate enterprise-readiness work. They must build on verified identities and explicit capabilities rather than bypassing the membership checks.
