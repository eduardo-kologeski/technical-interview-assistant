ALTER TABLE interviews ADD COLUMN seniority_snapshot VARCHAR(60) NOT NULL DEFAULT 'Não informada';
ALTER TABLE interviews ADD COLUMN client_company VARCHAR(255);
