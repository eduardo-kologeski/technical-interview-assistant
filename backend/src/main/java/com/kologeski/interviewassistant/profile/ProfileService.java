package com.kologeski.interviewassistant.profile;

import com.kologeski.interviewassistant.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import com.kologeski.interviewassistant.api.ResourceNotFoundException;

@Service
public class ProfileService {
    private final UserRepository users;
    private final UserTechnicalSkillRepository skills;
    private final InterviewAvailabilityRepository availability;
    private final Clock clock;

    public ProfileService(UserRepository users, UserTechnicalSkillRepository skills,
                          InterviewAvailabilityRepository availability, Clock clock) {
        this.users = users;
        this.skills = skills;
        this.availability = availability;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileSnapshot profile(User user) {
        return snapshot(user, false);
    }

    @Transactional
    public ProfileSnapshot updateProfile(User user, String name, String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailAndIdNot(normalizedEmail, user.getId()))
            throw new IllegalStateException("Este e-mail já está em uso.");
        user.updateProfile(name.trim(), normalizedEmail, Instant.now(clock));
        users.save(user);
        return snapshot(user, false);
    }

    @Transactional
    public SkillSnapshot addSkill(User user, String name, ProficiencyLevel level) {
        String cleanName = requiredName(name);
        String normalized = normalize(cleanName);
        if (skills.existsByUserIdAndNormalizedName(user.getId(), normalized))
            throw new IllegalStateException("Este conhecimento já foi adicionado.");
        return SkillSnapshot.from(skills.save(new UserTechnicalSkill(
                user, cleanName, normalized, level, Instant.now(clock))));
    }

    @Transactional
    public SkillSnapshot updateSkill(User user, Long id, String name, ProficiencyLevel level) {
        UserTechnicalSkill skill = skill(user, id);
        String cleanName = requiredName(name);
        String normalized = normalize(cleanName);
        boolean duplicate = skills.findByUserIdOrderByNameAsc(user.getId()).stream()
                .anyMatch(item -> !item.getId().equals(id) && item.getNormalizedName().equals(normalized));
        if (duplicate) throw new IllegalStateException("Este conhecimento já foi adicionado.");
        skill.update(cleanName, normalized, level, Instant.now(clock));
        return SkillSnapshot.from(skills.save(skill));
    }

    @Transactional
    public void removeSkill(User user, Long id) {
        skills.delete(skill(user, id));
    }

    @Transactional
    public AvailabilitySnapshot addAvailability(User user, LocalDate date, LocalTime start, LocalTime end) {
        validatePeriod(user, null, date, start, end);
        return AvailabilitySnapshot.from(availability.save(new InterviewAvailability(
                user, date, start, end, Instant.now(clock))));
    }

    @Transactional
    public AvailabilitySnapshot updateAvailability(User user, Long id, LocalDate date,
                                                   LocalTime start, LocalTime end) {
        InterviewAvailability slot = availability(user, id);
        validatePeriod(user, id, date, start, end);
        slot.update(date, start, end, Instant.now(clock));
        return AvailabilitySnapshot.from(availability.save(slot));
    }

    @Transactional
    public void removeAvailability(User user, Long id) {
        availability.delete(availability(user, id));
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySnapshot> availability(User user, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new IllegalStateException("O período final deve ser posterior ao inicial.");
        return availability.findByUserIdAndAvailabilityDateBetweenOrderByAvailabilityDateAscStartTimeAsc(
                        user.getId(), from, to).stream()
                .map(AvailabilitySnapshot::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<ProfileSnapshot> professionalProfiles(String technology, LocalDate date, LocalTime time,
                                                      Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        Page<User> page = users.searchInterviewers(technology, today, date, time, pageable);
        List<Long> ids = page.getContent().stream().map(User::getId).toList();
        if (ids.isEmpty()) return page.map(user -> snapshot(user, false));
        Map<Long, List<SkillSnapshot>> skillsByUser = skills
                .findByUserIdInOrderByUserIdAscNameAsc(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getUser().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(SkillSnapshot::from,
                                java.util.stream.Collectors.toList())));
        Map<Long, List<AvailabilitySnapshot>> availabilityByUser = availability
                .findByUserIdInAndAvailabilityDateGreaterThanEqualOrderByUserIdAscAvailabilityDateAscStartTimeAsc(
                        ids, today).stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getUser().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(AvailabilitySnapshot::from,
                                java.util.stream.Collectors.toList())));
        return page.map(user -> new ProfileSnapshot(user.getId(), user.getName(), user.getEmail(),
                skillsByUser.getOrDefault(user.getId(), List.of()),
                availabilityByUser.getOrDefault(user.getId(), List.of())));
    }

    private ProfileSnapshot snapshot(User user, boolean includeAvailability) {
        return new ProfileSnapshot(user.getId(), user.getName(), user.getEmail(),
                skills.findByUserIdOrderByNameAsc(user.getId()).stream().map(SkillSnapshot::from).toList(),
                includeAvailability
                        ? availability.findByUserIdOrderByAvailabilityDateAscStartTimeAsc(user.getId()).stream()
                                .map(AvailabilitySnapshot::from).toList()
                        : List.of());
    }

    private void validatePeriod(User user, Long ignoredId, LocalDate date, LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) throw new IllegalStateException("O horário final deve ser posterior ao inicial.");
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today) || (date.equals(today) && !start.isAfter(LocalTime.now(clock))))
            throw new IllegalStateException("Não é possível cadastrar disponibilidade em data ou horário passado.");
        boolean overlaps = availability
                .findByUserIdAndAvailabilityDateBetweenOrderByAvailabilityDateAscStartTimeAsc(
                        user.getId(), date, date).stream()
                .filter(slot -> ignoredId == null || !slot.getId().equals(ignoredId))
                .anyMatch(slot -> start.isBefore(slot.getEndTime()) && end.isAfter(slot.getStartTime()));
        if (overlaps) throw new IllegalStateException(
                "Este horário se sobrepõe a uma disponibilidade já cadastrada para esta data.");
    }

    private UserTechnicalSkill skill(User user, Long id) {
        return skills.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conhecimento não encontrado."));
    }

    private InterviewAvailability availability(User user, Long id) {
        return availability.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Disponibilidade não encontrada."));
    }

    static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String requiredName(String value) {
        if (value == null || value.trim().isBlank()) throw new IllegalStateException("Informe o conhecimento.");
        return value.trim();
    }

    public record ProfileSnapshot(Long id, String name, String email, List<SkillSnapshot> technicalSkills,
                                  List<AvailabilitySnapshot> availability) { }
    public record SkillSnapshot(Long id, String name, ProficiencyLevel proficiencyLevel) {
        static SkillSnapshot from(UserTechnicalSkill skill) {
            return new SkillSnapshot(skill.getId(), skill.getName(), skill.getProficiencyLevel());
        }
    }
    public record AvailabilitySnapshot(Long id, LocalDate availabilityDate, LocalTime startTime, LocalTime endTime) {
        static AvailabilitySnapshot from(InterviewAvailability value) {
            return new AvailabilitySnapshot(value.getId(), value.getAvailabilityDate(), value.getStartTime(), value.getEndTime());
        }
    }
}
