package com.kologeski.interviewassistant.company;

import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.audit.AuditTrailService;
import com.kologeski.interviewassistant.billing.EntitlementService;
import com.kologeski.interviewassistant.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final UserRepository users;
    private final CurrentActor actor;
    private final Clock clock;
    private final CompanyAuthorizationPolicy authorization;
    private final AuditTrailService audit;
    private final EntitlementService entitlements;

    public CompanyService(CompanyRepository companies, CompanyMembershipRepository memberships,
                          UserRepository users, CurrentActor actor, Clock clock,
                          CompanyAuthorizationPolicy authorization, AuditTrailService audit,
                          EntitlementService entitlements) {
        this.companies = companies; this.memberships = memberships; this.users = users;
        this.actor = actor; this.clock = clock; this.authorization = authorization; this.audit = audit;
        this.entitlements = entitlements;
    }

    @Transactional
    public Company create(String name) {
        Long userId = Long.valueOf(actor.required().id());
        users.findById(userId).orElseThrow();
        String base = slug(name);
        String candidate = base;
        int suffix = 2;
        while (companies.existsBySlugIgnoreCase(candidate)) candidate = base + "-" + suffix++;
        Instant now = Instant.now(clock);
        Company company = companies.save(new Company(name, candidate, userId, now));
        memberships.save(new CompanyMembership(company.id(), userId, CompanyRole.COMPANY_ADMIN, now));
        entitlements.provisionDefault(company.id());
        audit.record(company.id(), userId, "ADMIN", "COMPANY_CREATED", "COMPANY", company.id());
        return company;
    }

    @Transactional(readOnly = true)
    public List<CompanySummary> mine() {
        Long userId = Long.valueOf(actor.required().id());
        return memberships.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId).stream()
                .map(membership -> {
                    Company company = companies.findById(membership.companyId()).orElseThrow();
                    return new CompanySummary(company.id(), company.name(), company.slug(), membership.role());
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyMemberSummary> members(CompanyContext context) {
        CompanyContext.ActiveCompany active = authorization.require(CompanyCapability.VIEW_MEMBERS);
        return memberships.findByCompanyIdAndActiveTrueOrderByCreatedAtAsc(active.companyId()).stream()
                .map(membership -> {
                    User user = users.findById(membership.userId()).orElseThrow();
                    return new CompanyMemberSummary(user.getId(), user.getName(), user.getEmail(), membership.role());
                }).toList();
    }
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CompanyMemberSummary> members(org.springframework.data.domain.Pageable pageable) {
        var active = authorization.require(CompanyCapability.VIEW_MEMBERS);
        return memberships.findActiveMemberViews(active.companyId(), pageable)
                .map(member -> new CompanyMemberSummary(member.getId(), member.getName(),
                        member.getEmail(), member.getRole()));
    }

    @Transactional
    public CompanySummary rename(String name) {
        var active = authorization.require(CompanyCapability.MANAGE_MEMBERS);
        Company company = companies.findById(active.companyId()).orElseThrow();
        company.rename(name, Instant.now(clock)); companies.save(company);
        audit.record(active.companyId(), active.userId(), "ADMIN", "COMPANY_UPDATED", "COMPANY", company.id());
        return new CompanySummary(company.id(), company.name(), company.slug(), active.role());
    }

    @Transactional
    public CompanyMemberSummary changeRole(Long userId, CompanyRole role) {
        var active = authorization.require(CompanyCapability.MANAGE_MEMBERS);
        List<CompanyMembership> locked = memberships.lockActiveByCompanyId(active.companyId());
        CompanyMembership target = locked.stream().filter(item -> item.userId().equals(userId)).findFirst()
                .orElseThrow(() -> new com.kologeski.interviewassistant.api.ResourceNotFoundException("Membro não encontrado."));
        long administrators = locked.stream().filter(item -> item.role() == CompanyRole.COMPANY_ADMIN).count();
        if (target.role() == CompanyRole.COMPANY_ADMIN && role != CompanyRole.COMPANY_ADMIN && administrators == 1)
            throw new LastCompanyAdministratorException();
        target.changeRole(role, Instant.now(clock)); memberships.save(target);
        audit.record(active.companyId(), active.userId(), "SECURITY", "MEMBERSHIP_ROLE_CHANGED", "USER", userId);
        User user = users.findById(userId).orElseThrow();
        return new CompanyMemberSummary(user.getId(), user.getName(), user.getEmail(), target.role());
    }

    @Transactional
    public void revokeMember(CompanyContext context, Long userId) {
        CompanyContext.ActiveCompany active = authorization.require(CompanyCapability.MANAGE_MEMBERS);
        List<CompanyMembership> locked = memberships.lockActiveByCompanyId(active.companyId());
        CompanyMembership target = locked.stream().filter(item -> item.userId().equals(userId)).findFirst()
                .orElseThrow(() -> new com.kologeski.interviewassistant.api.ResourceNotFoundException("Membro não encontrado."));
        long administrators = locked.stream().filter(item -> item.role() == CompanyRole.COMPANY_ADMIN).count();
        if (target.role() == CompanyRole.COMPANY_ADMIN && administrators == 1)
            throw new LastCompanyAdministratorException();
        target.deactivate(Instant.now(clock));
        memberships.save(target);
        audit.record(active.companyId(), active.userId(), "SECURITY", "MEMBERSHIP_REVOKED", "USER", userId);
        log.info("event=company_membership_revoked companyId={} targetUserId={} actorId={}",
                active.companyId(), userId, active.userId());
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) throw new IllegalArgumentException("company name is required");
        return normalized.substring(0, Math.min(100, normalized.length()));
    }

    public record CompanySummary(UUID id, String name, String slug, CompanyRole role) { }
    public record CompanyMemberSummary(Long id, String name, String email, CompanyRole role) { }
    public static final class LastCompanyAdministratorException extends IllegalStateException {
        public LastCompanyAdministratorException() { super("A empresa precisa manter pelo menos um administrador ativo."); }
    }
}
