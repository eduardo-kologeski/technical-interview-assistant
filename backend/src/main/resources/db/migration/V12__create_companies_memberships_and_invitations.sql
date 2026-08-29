CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    created_by_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_companies_slug ON companies (LOWER(slug));

CREATE TABLE company_memberships (
    id BIGSERIAL PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_company_membership UNIQUE (company_id, user_id)
);

CREATE INDEX ix_company_memberships_user ON company_memberships(user_id, active);

CREATE TABLE company_invitations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    email VARCHAR(180) NOT NULL,
    role VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    invited_by_user_id BIGINT NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_company_invitations_token_hash ON company_invitations(token_hash);
CREATE INDEX ix_company_invitations_company_status ON company_invitations(company_id, status);
CREATE INDEX ix_company_invitations_email_status ON company_invitations(LOWER(email), status);

INSERT INTO companies (id, name, slug, created_by_user_id, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Ambiente legado', 'ambiente-legado', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO company_memberships (company_id, user_id, role, active, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000001', id, 'COMPANY_ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users;

ALTER TABLE job_vacancies ADD COLUMN company_id UUID;
UPDATE job_vacancies SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
ALTER TABLE job_vacancies ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE job_vacancies ADD CONSTRAINT fk_job_vacancies_company FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX ix_job_vacancies_company_active ON job_vacancies(company_id, active);

ALTER TABLE interviews ADD COLUMN company_id UUID;
UPDATE interviews SET company_id = '00000000-0000-0000-0000-000000000001' WHERE company_id IS NULL;
ALTER TABLE interviews ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE interviews ADD CONSTRAINT fk_interviews_company FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX ix_interviews_company_status ON interviews(company_id, status);
