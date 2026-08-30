# Authorization capabilities

Authorization is evaluated server-side against the authenticated user's active tenant membership. `X-Company-Id`
only selects one of that user's active memberships and never grants access.

| Capability | COMPANY_ADMIN | INTERVIEWER |
| --- | --- | --- |
| View members | Allow | Allow |
| Manage members | Allow | Deny |
| Manage invitations | Allow | Deny |
| View vacancies | Allow | Allow |
| Manage vacancies | Allow | Deny |
| View interviews | Allow | Allow |
| Manage interviews | Allow | Deny |
| Conduct assigned interview | Allow | Allow, participant check also required |
| Search interviewers | Allow | Allow |
| View audit log | Allow | Deny |

The policy is default-deny: a role receives only the explicitly mapped capabilities. Resource ownership and
participant checks remain mandatory in addition to capabilities. A capability never replaces tenant-scoped
repository queries.

New roles such as recruiter or hiring manager are intentionally deferred until their product workflows justify a
distinct least-privilege grant set. Custom enterprise RBAC is outside this baseline.
