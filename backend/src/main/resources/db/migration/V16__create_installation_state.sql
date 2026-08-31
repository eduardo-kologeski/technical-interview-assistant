CREATE TABLE installation_state (
    id SMALLINT PRIMARY KEY,
    setup_completed BOOLEAN NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_installation_state_singleton CHECK (id = 1)
);

INSERT INTO installation_state (id, setup_completed, completed_at)
SELECT 1,
       EXISTS (SELECT 1 FROM users),
       CASE WHEN EXISTS (SELECT 1 FROM users) THEN CURRENT_TIMESTAMP ELSE NULL END;
