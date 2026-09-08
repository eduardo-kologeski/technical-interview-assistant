CREATE INDEX idx_interviews_company_created_at
    ON interviews(company_id, created_at DESC);

CREATE INDEX idx_interviews_company_status_created_at
    ON interviews(company_id, status, created_at DESC);

CREATE INDEX idx_interviews_company_vacancy_created_at
    ON interviews(company_id, vacancy_id, created_at DESC);
