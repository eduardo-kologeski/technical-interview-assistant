ALTER TABLE assessment_reports
    ADD COLUMN reviewed_draft TEXT,
    ADD COLUMN human_conclusion TEXT,
    ADD COLUMN review_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN approval_idempotency_key UUID,
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN approved_by VARCHAR(100),
    ADD CONSTRAINT ck_assessment_reports_approval_state CHECK (
        status = 'DRAFT'
        OR (
            status = 'APPROVED'
            AND reviewed_draft IS NOT NULL
            AND LENGTH(TRIM(reviewed_draft)) > 0
            AND human_conclusion IS NOT NULL
            AND LENGTH(TRIM(human_conclusion)) > 0
            AND review_confirmed
            AND approval_idempotency_key IS NOT NULL
            AND approved_at IS NOT NULL
            AND approved_by IS NOT NULL
        )
    );

CREATE UNIQUE INDEX uq_assessment_reports_owner_approval_request
    ON assessment_reports(owner_user_id, approval_idempotency_key)
    WHERE approval_idempotency_key IS NOT NULL;
