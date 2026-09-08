CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    category VARCHAR(40) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(60),
    resource_id VARCHAR(100),
    actor_user_id BIGINT,
    correlation_id VARCHAR(64),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_events_company_time ON audit_events(company_id, occurred_at DESC);
CREATE INDEX idx_audit_events_company_category_time ON audit_events(company_id, category, occurred_at DESC);
