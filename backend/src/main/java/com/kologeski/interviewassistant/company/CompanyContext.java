package com.kologeski.interviewassistant.company;

import com.kologeski.interviewassistant.audit.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CompanyContext {
    public static final String HEADER = "X-Company-Id";
    private final CurrentActor actor;
    private final CompanyMembershipRepository memberships;
    private final HttpServletRequest request;

    public CompanyContext(CurrentActor actor, CompanyMembershipRepository memberships, HttpServletRequest request) {
        this.actor = actor;
        this.memberships = memberships;
        this.request = request;
    }

    public ActiveCompany required() {
        Long userId;
        try { userId = Long.valueOf(actor.required().id()); }
        catch (NumberFormatException invalidPrincipal) { throw new CompanyAccessDeniedException(); }
        String selected = request.getHeader(HEADER);
        if (selected != null && !selected.isBlank()) {
            UUID companyId;
            try { companyId = UUID.fromString(selected); }
            catch (IllegalArgumentException error) { throw new CompanyAccessDeniedException(); }
            CompanyMembership membership = memberships.findByCompanyIdAndUserIdAndActiveTrue(companyId, userId)
                    .orElseThrow(CompanyAccessDeniedException::new);
            return new ActiveCompany(companyId, userId, membership.role());
        }
        List<CompanyMembership> available = memberships.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);
        if (available.size() != 1) throw new CompanySelectionRequiredException();
        CompanyMembership membership = available.getFirst();
        return new ActiveCompany(membership.companyId(), userId, membership.role());
    }

    public record ActiveCompany(UUID companyId, Long userId, CompanyRole role) { }
    public static final class CompanyAccessDeniedException extends RuntimeException {
        public CompanyAccessDeniedException() { super("Acesso à empresa negado."); }
    }
    public static final class CompanySelectionRequiredException extends RuntimeException {
        public CompanySelectionRequiredException() { super("Selecione uma empresa para continuar."); }
    }
}
