ALTER TABLE assessment_studies
    ADD COLUMN evaluation_reconciliation TEXT,
    ADD COLUMN reconciliation_finalized_at TIMESTAMPTZ,
    ADD COLUMN reconciliation_finalized_by VARCHAR(100),
    ADD CONSTRAINT ck_assessment_studies_reconciliation_finalized CHECK (
      (reconciliation_finalized_at IS NULL AND reconciliation_finalized_by IS NULL) OR
      (reconciliation_finalized_at IS NOT NULL AND reconciliation_finalized_by IS NOT NULL AND evaluation_reconciliation IS NOT NULL)
    );

CREATE TABLE academic_peer_evaluations (
    id UUID PRIMARY KEY,
    study_id UUID NOT NULL REFERENCES assessment_studies(id) ON DELETE CASCADE,
    assessment_id UUID NOT NULL,
    owner_user_id BIGINT NOT NULL,
    reviewer_code VARCHAR(80) NOT NULL CHECK (length(trim(reviewer_code)) BETWEEN 2 AND 80),
    evaluation TEXT NOT NULL CHECK (length(evaluation) > 0),
    finalized_at TIMESTAMPTZ,
    finalized_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT fk_academic_peer_evaluations_assessment FOREIGN KEY (assessment_id, owner_user_id)
      REFERENCES interviews(id, owner_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_academic_peer_evaluations_finalized CHECK (
      (finalized_at IS NULL AND finalized_by IS NULL) OR
      (finalized_at IS NOT NULL AND finalized_by IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_academic_peer_evaluations_reviewer
    ON academic_peer_evaluations(study_id, lower(trim(reviewer_code)));

CREATE INDEX ix_academic_peer_evaluations_assessment
    ON academic_peer_evaluations(assessment_id, owner_user_id);
