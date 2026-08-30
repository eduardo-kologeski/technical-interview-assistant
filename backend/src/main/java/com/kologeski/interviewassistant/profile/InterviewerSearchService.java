package com.kologeski.interviewassistant.profile;

import com.kologeski.interviewassistant.vacancy.JobVacancyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.time.*;
import java.util.*;
import com.kologeski.interviewassistant.company.*;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class InterviewerSearchService {
    private final ProfileService profiles;
    private final JobVacancyService vacancies;
    private final Clock clock;
    private final CompanyContext companyContext;
    private final CompanyMembershipRepository memberships;
    private final CompanyAuthorizationPolicy authorization;

    @Autowired
    public InterviewerSearchService(ProfileService profiles, JobVacancyService vacancies, Clock clock,
                                    CompanyContext companyContext, CompanyMembershipRepository memberships,
                                    CompanyAuthorizationPolicy authorization) {
        this.profiles = profiles; this.vacancies = vacancies; this.clock = clock;
        this.companyContext = companyContext; this.memberships = memberships; this.authorization = authorization;
    }
    public InterviewerSearchService(ProfileService profiles, JobVacancyService vacancies, Clock clock) {
        this(profiles, vacancies, clock, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<InterviewerResult> search(UUID vacancyId, String technology, LocalDate date, LocalTime time,
                                          Pageable pageable) {
        List<String> requirements = vacancyId == null ? List.of() : vacancies.get(vacancyId).requirements();
        if (authorization != null) authorization.require(CompanyCapability.SEARCH_INTERVIEWERS);
        String technologyFilter = technology == null ? "" : ProfileService.normalize(technology);
        Page<ProfileService.ProfileSnapshot> profilesPage;
        if (companyContext == null) {
            profilesPage = profiles.professionalProfiles(technologyFilter, date, time, pageable);
        } else {
            UUID companyId = companyContext.required().companyId();
            List<Long> userIds = memberships.findByCompanyIdAndActiveTrueOrderByCreatedAtAsc(companyId)
                    .stream().map(CompanyMembership::userId).toList();
            profilesPage = profiles.professionalProfiles(userIds, technologyFilter, date, time, pageable);
        }
        List<InterviewerResult> results = profilesPage.getContent().stream()
                .map(profile -> result(profile, requirements, date))
                .sorted(Comparator.comparingInt(InterviewerResult::matchedCount).reversed()
                        .thenComparing(Comparator.comparingInt(InterviewerResult::proficiencyWeight).reversed())
                        .thenComparing(InterviewerResult::name))
                .toList();
        return new PageImpl<>(results, pageable, profilesPage.getTotalElements());
    }

    private InterviewerResult result(ProfileService.ProfileSnapshot profile, List<String> requirements, LocalDate date) {
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
        List<ProfileService.AvailabilitySnapshot> relevantAvailability = profile.availability().stream()
                .filter(slot -> !slot.availabilityDate().isBefore(LocalDate.now(clock)))
                .filter(slot -> date == null || slot.availabilityDate().equals(date))
                .limit(date == null ? 8 : Integer.MAX_VALUE)
                .toList();
        return new InterviewerResult(profile.id(), profile.name(), profile.technicalSkills(),
                relevantAvailability, requirements, matches, matched, requirements.size(), proficiencyWeight);
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
