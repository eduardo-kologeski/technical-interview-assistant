package com.kologeski.interviewassistant.profile;

import com.kologeski.interviewassistant.vacancy.JobVacancyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class InterviewerSearchService {
    private final ProfileService profiles;
    private final JobVacancyService vacancies;

    public InterviewerSearchService(ProfileService profiles, JobVacancyService vacancies) {
        this.profiles = profiles;
        this.vacancies = vacancies;
    }

    @Transactional(readOnly = true)
    public List<InterviewerResult> search(UUID vacancyId, String technology, DayOfWeek day, LocalTime time) {
        List<String> requirements = vacancyId == null ? List.of() : vacancies.get(vacancyId).requirements();
        String technologyFilter = technology == null ? "" : ProfileService.normalize(technology);
        return profiles.allProfessionalProfiles().stream()
                .map(profile -> result(profile, requirements))
                .filter(result -> technologyFilter.isBlank() || result.allSkills().stream()
                        .anyMatch(skill -> ProfileService.normalize(skill.name()).equals(technologyFilter)))
                .filter(result -> day == null || result.availability().stream()
                        .anyMatch(slot -> slot.dayOfWeek() == day && contains(slot, time)))
                .filter(result -> day != null || time == null || result.availability().stream()
                        .anyMatch(slot -> contains(slot, time)))
                .sorted(Comparator.comparingInt(InterviewerResult::matchedCount).reversed()
                        .thenComparing(Comparator.comparingInt(InterviewerResult::proficiencyWeight).reversed())
                        .thenComparing(InterviewerResult::name))
                .toList();
    }

    private InterviewerResult result(ProfileService.ProfileSnapshot profile, List<String> requirements) {
        Map<String, ProfileService.SkillSnapshot> byName = new HashMap<>();
        profile.technicalSkills().forEach(skill -> byName.put(ProfileService.normalize(skill.name()), skill));
        List<RequirementMatch> matches = requirements.stream().map(requirement -> {
            ProfileService.SkillSnapshot skill = byName.get(ProfileService.normalize(requirement));
            return new RequirementMatch(requirement, skill != null,
                    skill == null ? null : skill.proficiencyLevel());
        }).toList();
        int matched = (int) matches.stream().filter(RequirementMatch::matched).count();
        int proficiencyWeight = matches.stream().map(RequirementMatch::proficiencyLevel)
                .filter(Objects::nonNull).mapToInt(Enum::ordinal).sum();
        return new InterviewerResult(profile.id(), profile.name(), profile.technicalSkills(),
                profile.availability(), requirements, matches, matched, requirements.size(), proficiencyWeight);
    }

    private static boolean contains(ProfileService.AvailabilitySnapshot slot, LocalTime time) {
        return time == null || (!time.isBefore(slot.startTime()) && time.isBefore(slot.endTime()));
    }

    public record RequirementMatch(String name, boolean matched, ProficiencyLevel proficiencyLevel) { }
    public record InterviewerResult(Long id, String name, List<ProfileService.SkillSnapshot> allSkills,
                                    List<ProfileService.AvailabilitySnapshot> availability,
                                    List<String> requirements, List<RequirementMatch> requirementMatches,
                                    int matchedCount, int requirementCount, int proficiencyWeight) { }
}
