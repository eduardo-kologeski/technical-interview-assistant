package com.kologeski.interviewassistant.company;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CompanyAuthorizationPolicy {
    private static final Map<CompanyRole, Set<CompanyCapability>> GRANTS = Map.of(
            CompanyRole.COMPANY_ADMIN, Collections.unmodifiableSet(EnumSet.allOf(CompanyCapability.class)),
            CompanyRole.INTERVIEWER, Collections.unmodifiableSet(EnumSet.of(
                    CompanyCapability.VIEW_MEMBERS, CompanyCapability.VIEW_VACANCIES,
                    CompanyCapability.VIEW_INTERVIEWS, CompanyCapability.CONDUCT_ASSIGNED_INTERVIEW,
                    CompanyCapability.SEARCH_INTERVIEWERS)));
    private final CompanyContext context;

    public CompanyAuthorizationPolicy(CompanyContext context) { this.context = context; }

    public CompanyContext.ActiveCompany require(CompanyCapability capability) {
        CompanyContext.ActiveCompany active = context.required();
        if (!allows(active.role(), capability)) throw new CompanyContext.CompanyAccessDeniedException();
        return active;
    }

    public boolean allows(CompanyRole role, CompanyCapability capability) {
        return role != null && capability != null && GRANTS.getOrDefault(role, Set.of()).contains(capability);
    }
}
