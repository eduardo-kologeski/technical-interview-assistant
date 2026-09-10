ALTER TABLE assessment_studies
    ADD COLUMN limitation_analysis TEXT,
    ADD COLUMN limitation_analysis_finalized_at TIMESTAMPTZ,
    ADD COLUMN limitation_analysis_finalized_by VARCHAR(100),
    ADD CONSTRAINT ck_assessment_studies_limitation_analysis_finalized CHECK (
      (limitation_analysis_finalized_at IS NULL AND limitation_analysis_finalized_by IS NULL) OR
      (limitation_analysis_finalized_at IS NOT NULL AND limitation_analysis_finalized_by IS NOT NULL AND limitation_analysis IS NOT NULL)
    );

ALTER TABLE assessment_report_generations
    ADD COLUMN limitation_reference UUID;
