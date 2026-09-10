package com.kologeski.interviewassistant.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface UserTechnicalSkillRepository extends JpaRepository<UserTechnicalSkill, Long> {
    List<UserTechnicalSkill> findByUserIdOrderByNameAsc(Long userId);
    List<UserTechnicalSkill> findAllByOrderByUserIdAscNameAsc();
    List<UserTechnicalSkill> findByUserIdInOrderByUserIdAscNameAsc(Collection<Long> userIds);
    boolean existsByUserIdAndNormalizedName(Long userId, String normalizedName);
    Optional<UserTechnicalSkill> findByIdAndUserId(Long id, Long userId);
    long deleteByUserId(Long userId);
}
