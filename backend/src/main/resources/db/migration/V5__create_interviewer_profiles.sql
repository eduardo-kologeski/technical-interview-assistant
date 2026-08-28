CREATE TABLE user_technical_skills (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    proficiency_level VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_user_technical_skills_name
    ON user_technical_skills(user_id, normalized_name);
CREATE INDEX ix_user_technical_skills_user ON user_technical_skills(user_id);

CREATE TABLE interview_availability (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week VARCHAR(12) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_interview_availability_period CHECK (end_time > start_time)
);

CREATE INDEX ix_interview_availability_user_day
    ON interview_availability(user_id, day_of_week);
