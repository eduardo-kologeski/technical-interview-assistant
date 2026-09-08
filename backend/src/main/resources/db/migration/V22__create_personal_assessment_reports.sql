ALTER TABLE interviews
    ADD CONSTRAINT uq_interviews_id_owner UNIQUE (id, owner_user_id);

CREATE TABLE assessment_reports (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL,
    owner_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_version BIGINT NOT NULL,
    idempotency_key UUID NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    context_snapshot TEXT NOT NULL,
    original_draft TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT fk_assessment_reports_personal_assessment
        FOREIGN KEY (assessment_id, owner_user_id)
        REFERENCES interviews(id, owner_user_id) ON DELETE CASCADE,
    CONSTRAINT ck_assessment_reports_status CHECK (status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT ck_assessment_reports_source_version CHECK (source_version >= 0),
    CONSTRAINT ck_assessment_reports_prompt_version CHECK (LENGTH(TRIM(prompt_version)) > 0),
    CONSTRAINT ck_assessment_reports_model_name CHECK (LENGTH(TRIM(model_name)) > 0),
    CONSTRAINT ck_assessment_reports_context CHECK (LENGTH(context_snapshot) > 0),
    CONSTRAINT ck_assessment_reports_original CHECK (LENGTH(original_draft) > 0),
    CONSTRAINT uq_assessment_reports_assessment UNIQUE (assessment_id),
    CONSTRAINT uq_assessment_reports_owner_request UNIQUE (owner_user_id, idempotency_key)
);

CREATE INDEX ix_assessment_reports_owner_created
    ON assessment_reports(owner_user_id, created_at DESC, id);
