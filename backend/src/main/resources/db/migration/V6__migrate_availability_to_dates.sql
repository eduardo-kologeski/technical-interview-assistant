ALTER TABLE interview_availability ADD COLUMN availability_date DATE;

UPDATE interview_availability
SET availability_date = CURRENT_DATE + (
    (
        CASE day_of_week
            WHEN 'MONDAY' THEN 1
            WHEN 'TUESDAY' THEN 2
            WHEN 'WEDNESDAY' THEN 3
            WHEN 'THURSDAY' THEN 4
            WHEN 'FRIDAY' THEN 5
            WHEN 'SATURDAY' THEN 6
            WHEN 'SUNDAY' THEN 7
        END
        - CAST(EXTRACT(ISODOW FROM CURRENT_DATE) AS INTEGER) + 7
    ) % 7
);

ALTER TABLE interview_availability ALTER COLUMN availability_date SET NOT NULL;
ALTER TABLE interview_availability ALTER COLUMN day_of_week DROP NOT NULL;

CREATE INDEX ix_interview_availability_user_date
    ON interview_availability(user_id, availability_date, start_time);
