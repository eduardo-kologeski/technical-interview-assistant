ALTER TABLE assessment_studies
    ADD COLUMN report_evaluation TEXT,
    ADD COLUMN evaluation_finalized_at TIMESTAMPTZ,
    ADD COLUMN evaluation_finalized_by VARCHAR(100),
    ADD CONSTRAINT ck_assessment_studies_evaluation_finalized CHECK (
      (evaluation_finalized_at IS NULL AND evaluation_finalized_by IS NULL)
      OR
      (evaluation_finalized_at IS NOT NULL AND evaluation_finalized_by IS NOT NULL AND report_evaluation IS NOT NULL)
    );
