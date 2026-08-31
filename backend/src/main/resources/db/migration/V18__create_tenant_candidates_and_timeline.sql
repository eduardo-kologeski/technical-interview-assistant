CREATE TABLE candidates (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    display_name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    CONSTRAINT uk_candidate_company_name UNIQUE (company_id, normalized_name)
);

CREATE TABLE candidate_vacancies (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    candidate_id UUID NOT NULL REFERENCES candidates(id),
    vacancy_id UUID NOT NULL REFERENCES job_vacancies(id),
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_candidate_vacancy UNIQUE (candidate_id, vacancy_id)
);

CREATE TABLE candidate_timeline_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    candidate_id UUID NOT NULL REFERENCES candidates(id),
    event_type VARCHAR(40) NOT NULL,
    vacancy_id UUID REFERENCES job_vacancies(id),
    interview_id UUID REFERENCES interviews(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id VARCHAR(255) NOT NULL
);

INSERT INTO candidates (id, company_id, display_name, normalized_name, created_at, created_by, updated_at, updated_by)
SELECT (MD5(company_id::text || ':' || LOWER(TRIM(candidate_name))))::uuid, company_id,
       MIN(TRIM(candidate_name)), LOWER(TRIM(candidate_name)), MIN(created_at), MIN(created_by), MAX(updated_at), MAX(updated_by)
FROM interviews GROUP BY company_id, LOWER(TRIM(candidate_name));

INSERT INTO candidate_vacancies (id, company_id, candidate_id, vacancy_id, first_seen_at, last_seen_at)
SELECT (MD5(company_id::text || ':' || LOWER(TRIM(candidate_name)) || ':' || vacancy_id::text))::uuid,
       company_id, (MD5(company_id::text || ':' || LOWER(TRIM(candidate_name))))::uuid, vacancy_id,
       MIN(created_at), MAX(updated_at)
FROM interviews GROUP BY company_id, LOWER(TRIM(candidate_name)), vacancy_id;

ALTER TABLE interviews ADD COLUMN candidate_id UUID;
UPDATE interviews SET candidate_id = (MD5(company_id::text || ':' || LOWER(TRIM(candidate_name))))::uuid;
ALTER TABLE interviews ALTER COLUMN candidate_id SET NOT NULL;
ALTER TABLE interviews ADD CONSTRAINT fk_interviews_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id);

INSERT INTO candidate_timeline_events (id, company_id, candidate_id, event_type, vacancy_id, interview_id, occurred_at, actor_id)
SELECT (MD5(id::text || ':INTERVIEW_STARTED'))::uuid, company_id, candidate_id, 'INTERVIEW_STARTED', vacancy_id, id,
       COALESCE(started_at, created_at), created_by FROM interviews;

CREATE INDEX ix_candidates_company_name ON candidates(company_id, normalized_name);
CREATE INDEX ix_candidate_vacancies_company ON candidate_vacancies(company_id, candidate_id);
CREATE INDEX ix_candidate_timeline ON candidate_timeline_events(company_id, candidate_id, occurred_at DESC);
