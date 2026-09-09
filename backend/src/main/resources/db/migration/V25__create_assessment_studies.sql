CREATE TABLE assessment_studies (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL,
    owner_user_id BIGINT NOT NULL,
    source_version BIGINT NOT NULL CHECK (source_version >= 0),
    manual_report TEXT NOT NULL CHECK (LENGTH(manual_report) > 0),
    context_hash VARCHAR(64),
    manual_finalized_at TIMESTAMPTZ,
    manual_finalized_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT uq_assessment_studies_assessment UNIQUE (assessment_id),
    CONSTRAINT fk_assessment_studies_assessment FOREIGN KEY (assessment_id,owner_user_id)
      REFERENCES interviews(id,owner_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_assessment_studies_finalized CHECK ((manual_finalized_at IS NULL AND manual_finalized_by IS NULL AND context_hash IS NULL) OR (manual_finalized_at IS NOT NULL AND manual_finalized_by IS NOT NULL AND context_hash ~ '^[0-9a-f]{64}$'))
);
