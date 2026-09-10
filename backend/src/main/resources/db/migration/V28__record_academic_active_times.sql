ALTER TABLE assessment_studies
    ADD COLUMN timing_version VARCHAR(40),
    ADD COLUMN manual_active_millis BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_assessment_studies_manual_active_time CHECK (manual_active_millis BETWEEN 0 AND 604800000);

ALTER TABLE assessment_reports
    ADD COLUMN generation_latency_millis BIGINT,
    ADD COLUMN review_active_millis BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_assessment_reports_generation_latency CHECK (generation_latency_millis IS NULL OR generation_latency_millis BETWEEN 0 AND 86400000),
    ADD CONSTRAINT ck_assessment_reports_review_active_time CHECK (review_active_millis BETWEEN 0 AND 604800000);
