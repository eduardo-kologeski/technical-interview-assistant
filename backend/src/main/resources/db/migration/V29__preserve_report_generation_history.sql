CREATE TABLE assessment_report_generations (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES assessment_reports(id) ON DELETE CASCADE,
    assessment_id UUID NOT NULL,
    owner_user_id BIGINT NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    generation_kind VARCHAR(20) NOT NULL CHECK (generation_kind IN ('ORIGINAL', 'EXPERIMENTAL')),
    idempotency_key UUID NOT NULL,
    justification VARCHAR(2000),
    prompt_version VARCHAR(100) NOT NULL CHECK (length(trim(prompt_version)) > 0),
    model_name VARCHAR(200) NOT NULL CHECK (length(trim(model_name)) > 0),
    generated_draft TEXT NOT NULL CHECK (length(generated_draft) > 0),
    generation_latency_millis BIGINT CHECK (generation_latency_millis IS NULL OR generation_latency_millis BETWEEN 0 AND 86400000),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    CONSTRAINT uq_assessment_report_generations_sequence UNIQUE (report_id, sequence_number),
    CONSTRAINT uq_assessment_report_generations_request UNIQUE (owner_user_id, idempotency_key),
    CONSTRAINT ck_assessment_report_generations_justification CHECK (
      (generation_kind = 'ORIGINAL' AND justification IS NULL) OR
      (generation_kind = 'EXPERIMENTAL' AND length(trim(justification)) BETWEEN 10 AND 2000)
    ),
    CONSTRAINT fk_assessment_report_generations_assessment FOREIGN KEY (assessment_id, owner_user_id)
      REFERENCES interviews(id, owner_user_id) ON DELETE CASCADE
);

CREATE INDEX ix_assessment_report_generations_report_created
    ON assessment_report_generations(report_id, sequence_number);

INSERT INTO assessment_report_generations (
    id, report_id, assessment_id, owner_user_id, sequence_number, generation_kind,
    idempotency_key, justification, prompt_version, model_name, generated_draft,
    generation_latency_millis, created_at, created_by
)
SELECT gen_random_uuid(), id, assessment_id, owner_user_id, 1, 'ORIGINAL',
       idempotency_key, NULL, prompt_version, model_name, original_draft,
       generation_latency_millis, created_at, created_by
FROM assessment_reports;
