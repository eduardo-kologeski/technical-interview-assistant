CREATE TABLE report_generation_claims (
    assessment_id UUID PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    idempotency_key UUID NOT NULL,
    source_version BIGINT NOT NULL CHECK (source_version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_report_generation_claims_owner_key UNIQUE (owner_user_id, idempotency_key),
    CONSTRAINT fk_report_generation_claims_assessment FOREIGN KEY (assessment_id, owner_user_id)
      REFERENCES interviews(id, owner_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_report_generation_claims_expiry CHECK (expires_at > created_at)
);
