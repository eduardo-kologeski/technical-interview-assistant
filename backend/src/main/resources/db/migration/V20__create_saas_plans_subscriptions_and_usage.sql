CREATE TABLE saas_plans (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    trial_days INTEGER NOT NULL CHECK (trial_days >= 0),
    max_active_vacancies INTEGER NOT NULL CHECK (max_active_vacancies >= 0),
    max_members INTEGER NOT NULL CHECK (max_members >= 0),
    max_monthly_interviews INTEGER NOT NULL CHECK (max_monthly_interviews >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE saas_entitlements (
    plan_code VARCHAR(40) NOT NULL REFERENCES saas_plans(code),
    feature_key VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (plan_code, feature_key)
);

CREATE TABLE saas_subscriptions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL UNIQUE REFERENCES companies(id),
    plan_code VARCHAR(40) NOT NULL REFERENCES saas_plans(code),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    trial_ends_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE saas_usage (
    company_id UUID NOT NULL REFERENCES companies(id),
    metric VARCHAR(60) NOT NULL,
    period_start DATE NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (company_id, metric, period_start)
);

INSERT INTO saas_plans(code,name,trial_days,max_active_vacancies,max_members,max_monthly_interviews,active,created_at)
VALUES ('STARTER','Starter',30,25,10,100,TRUE,CURRENT_TIMESTAMP),
       ('TEAM','Team',30,200,50,2000,TRUE,CURRENT_TIMESTAMP);

INSERT INTO saas_entitlements(plan_code,feature_key,enabled)
VALUES ('STARTER','vacancy.create',TRUE),('STARTER','interview.create',TRUE),
       ('STARTER','member.invite',TRUE),('STARTER','data.export',TRUE),
       ('TEAM','vacancy.create',TRUE),('TEAM','interview.create',TRUE),
       ('TEAM','member.invite',TRUE),('TEAM','data.export',TRUE);

INSERT INTO saas_subscriptions(id,company_id,plan_code,status,started_at,trial_ends_at,updated_at)
SELECT gen_random_uuid(),id,'STARTER','TRIALING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 days',CURRENT_TIMESTAMP
FROM companies;

INSERT INTO saas_usage(company_id,metric,period_start,quantity,updated_at)
SELECT company_id,'MONTHLY_INTERVIEWS',date_trunc('month',created_at)::date,count(*),CURRENT_TIMESTAMP
FROM interviews
GROUP BY company_id,date_trunc('month',created_at)::date;

CREATE INDEX idx_saas_subscriptions_plan_status ON saas_subscriptions(plan_code,status);
CREATE INDEX idx_saas_usage_metric_period ON saas_usage(metric,period_start);
