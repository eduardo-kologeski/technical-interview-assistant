CREATE TABLE interview_participants (
    id BIGSERIAL PRIMARY KEY,
    interview_id UUID NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    assessment_data TEXT NOT NULL DEFAULT '{}',
    version BIGINT NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_interview_participant UNIQUE (interview_id, user_id)
);

CREATE INDEX ix_interview_participants_user_status ON interview_participants(user_id, status);

INSERT INTO interview_participants (interview_id, user_id, status, assessment_data, version, completed_at, created_at, updated_at)
SELECT i.id, CAST(i.interviewer_id AS BIGINT),
       CASE WHEN i.status = 'COMPLETED' THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,
       i.assessment_data, i.version, i.completed_at, i.created_at, i.updated_at
FROM interviews i
WHERE i.interviewer_id ~ '^[0-9]+$'
  AND EXISTS (SELECT 1 FROM users u WHERE u.id = CAST(i.interviewer_id AS BIGINT));
