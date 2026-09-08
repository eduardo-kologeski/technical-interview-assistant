ALTER TABLE interviews ADD COLUMN owner_user_id BIGINT REFERENCES users(id);
ALTER TABLE interviews ADD COLUMN title VARCHAR(160);
ALTER TABLE interviews ADD COLUMN context VARCHAR(5000);
ALTER TABLE interviews ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE interviews ALTER COLUMN vacancy_id DROP NOT NULL;
ALTER TABLE interviews ALTER COLUMN candidate_id DROP NOT NULL;
ALTER TABLE interviews ALTER COLUMN vacancy_title_snapshot DROP NOT NULL;
ALTER TABLE interviews ALTER COLUMN seniority_snapshot DROP NOT NULL;

ALTER TABLE interviews ADD CONSTRAINT ck_interviews_exclusive_scope CHECK (
    (company_id IS NOT NULL AND owner_user_id IS NULL)
    OR (company_id IS NULL AND owner_user_id IS NOT NULL)
);
ALTER TABLE interviews ADD CONSTRAINT ck_interviews_scope_data CHECK (
    (company_id IS NOT NULL AND vacancy_id IS NOT NULL AND candidate_id IS NOT NULL
        AND vacancy_title_snapshot IS NOT NULL AND seniority_snapshot IS NOT NULL)
    OR (owner_user_id IS NOT NULL AND title IS NOT NULL AND LENGTH(TRIM(title)) > 0
        AND vacancy_id IS NULL AND candidate_id IS NULL)
);
CREATE INDEX ix_interviews_owner_updated ON interviews(owner_user_id, updated_at DESC, id)
    WHERE owner_user_id IS NOT NULL;
