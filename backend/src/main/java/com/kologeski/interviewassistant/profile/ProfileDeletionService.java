package com.kologeski.interviewassistant.profile;

import org.springframework.stereotype.Service;

@Service
public class ProfileDeletionService {
    private final UserTechnicalSkillRepository skills;
    private final InterviewAvailabilityRepository availability;

    public ProfileDeletionService(UserTechnicalSkillRepository skills, InterviewAvailabilityRepository availability) {
        this.skills = skills;
        this.availability = availability;
    }

    public void deleteForUser(Long userId) {
        skills.deleteByUserId(userId);
        availability.deleteByUserId(userId);
    }
}
