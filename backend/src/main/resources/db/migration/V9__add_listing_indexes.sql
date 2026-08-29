CREATE INDEX idx_job_vacancies_updated_at ON job_vacancies(updated_at DESC);
CREATE INDEX idx_interviews_updated_at ON interviews(updated_at DESC);
CREATE INDEX idx_interviews_status_updated_at ON interviews(status, updated_at DESC);
