CREATE TABLE job_vacancies (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);

CREATE TABLE job_vacancy_requirements (
    vacancy_id UUID NOT NULL REFERENCES job_vacancies(id),
    requirement_name VARCHAR(255) NOT NULL
);

CREATE TABLE interviews (
    id UUID PRIMARY KEY,
    vacancy_id UUID NOT NULL REFERENCES job_vacancies(id),
    candidate_name VARCHAR(255) NOT NULL,
    vacancy_title_snapshot VARCHAR(255) NOT NULL,
    interviewer_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    assessment_finalized_at TIMESTAMPTZ,
    assessment_finalized_by VARCHAR(255),
    last_pdf_generated_at TIMESTAMPTZ,
    last_pdf_generated_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);

CREATE TABLE interview_requirement_snapshots (
    interview_id UUID NOT NULL REFERENCES interviews(id),
    requirement_name VARCHAR(255) NOT NULL
);

CREATE INDEX idx_job_vacancies_active ON job_vacancies(active);
CREATE INDEX idx_interviews_vacancy ON interviews(vacancy_id);
CREATE INDEX idx_interviews_interviewer ON interviews(interviewer_id);
