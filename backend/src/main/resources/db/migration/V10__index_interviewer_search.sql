CREATE INDEX idx_user_technical_skills_normalized_user
    ON user_technical_skills(normalized_name, user_id);

CREATE INDEX idx_interview_availability_date_time_user
    ON interview_availability(availability_date, start_time, end_time, user_id);
