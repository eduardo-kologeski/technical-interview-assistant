package com.kologeski.interviewassistant.billing;

import com.kologeski.interviewassistant.company.CompanyAuthorizationPolicy;
import com.kologeski.interviewassistant.company.CompanyCapability;
import com.kologeski.interviewassistant.deployment.DeploymentProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class EntitlementService {
    public static final String DEFAULT_PLAN = "STARTER";
    private final JdbcClient jdbc;
    private final CompanyAuthorizationPolicy authorization;
    private final Clock clock;
    private final DeploymentProperties deployment;

    public EntitlementService(JdbcClient jdbc, CompanyAuthorizationPolicy authorization, Clock clock,
                              DeploymentProperties deployment) {
        this.jdbc = jdbc; this.authorization = authorization; this.clock = clock; this.deployment = deployment;
    }

    @Transactional
    public void provisionDefault(UUID companyId) {
        if (!deployment.billingEnabled()) return;
        Instant now = clock.instant();
        jdbc.sql("""
                insert into saas_subscriptions(id,company_id,plan_code,status,started_at,trial_ends_at,updated_at)
                values (:id,:companyId,:plan,'TRIALING',:now,:trialEnd,:now)
                on conflict (company_id) do nothing
                """).param("id", UUID.randomUUID()).param("companyId", companyId).param("plan", DEFAULT_PLAN)
                .param("now", Timestamp.from(now)).param("trialEnd", Timestamp.from(now.plus(Duration.ofDays(30))))
                .update();
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot current() {
        UUID companyId = authorization.require(CompanyCapability.VIEW_INTERVIEWS).companyId();
        return snapshot(companyId);
    }

    @Transactional
    public void requireVacancyCreation(UUID companyId) {
        if (!deployment.billingEnabled()) return;
        PlanState state = require(companyId, "vacancy.create");
        lock(companyId);
        long current = jdbc.sql("select count(*) from job_vacancies where company_id=:companyId and active=true")
                .param("companyId", companyId).query(Long.class).single();
        if (current >= state.maxActiveVacancies()) throw new QuotaExceededException("Vagas ativas", current, state.maxActiveVacancies());
    }

    @Transactional
    public void requireMemberInvitation(UUID companyId) {
        requireMemberInvitation(companyId, 0);
    }

    @Transactional
    public void requireMemberInvitation(UUID companyId, long replacingActiveInvitations) {
        if (!deployment.billingEnabled()) return;
        PlanState state = require(companyId, "member.invite");
        lock(companyId);
        long current = jdbc.sql("""
                select (select count(*) from company_memberships where company_id=:companyId and active=true) +
                       (select count(*) from company_invitations where company_id=:companyId and status='PENDING' and expires_at>:now)
                """).param("companyId", companyId).param("now", Timestamp.from(clock.instant()))
                .query(Long.class).single();
        long effective = Math.max(0, current - replacingActiveInvitations);
        if (effective >= state.maxMembers()) throw new QuotaExceededException("Pessoas e convites pendentes", effective, state.maxMembers());
    }

    @Transactional
    public void requireMembershipSeat(UUID companyId) {
        if (!deployment.billingEnabled()) return;
        PlanState state = require(companyId, "member.invite");
        lock(companyId);
        long current = jdbc.sql("select count(*) from company_memberships where company_id=:companyId and active=true")
                .param("companyId", companyId).query(Long.class).single();
        if (current >= state.maxMembers()) throw new QuotaExceededException("Pessoas", current, state.maxMembers());
    }

    @Transactional
    public void consumeInterview(UUID companyId) {
        if (!deployment.billingEnabled()) return;
        PlanState state = require(companyId, "interview.create");
        if (state.maxMonthlyInterviews() <= 0) throw new QuotaExceededException("Entrevistas no mês", 0, 0);
        LocalDate period = YearMonth.now(clock).atDay(1);
        Optional<Long> quantity = jdbc.sql("""
                insert into saas_usage(company_id,metric,period_start,quantity,updated_at)
                values (:companyId,'MONTHLY_INTERVIEWS',:period,1,:now)
                on conflict (company_id,metric,period_start) do update
                   set quantity=saas_usage.quantity+1,updated_at=excluded.updated_at
                 where saas_usage.quantity < :quota
                returning quantity
                """).param("companyId", companyId).param("period", period)
                .param("now", Timestamp.from(clock.instant())).param("quota", state.maxMonthlyInterviews())
                .query(Long.class).optional();
        if (quantity.isEmpty()) {
            long current = usage(companyId, period);
            throw new QuotaExceededException("Entrevistas no mês", current, state.maxMonthlyInterviews());
        }
    }

    private SubscriptionSnapshot snapshot(UUID companyId) {
        PlanState state = state(companyId).orElseThrow(EntitlementDeniedException::new);
        LocalDate period = YearMonth.now(clock).atDay(1);
        long vacancies = jdbc.sql("select count(*) from job_vacancies where company_id=:companyId and active=true")
                .param("companyId", companyId).query(Long.class).single();
        long members = jdbc.sql("select count(*) from company_memberships where company_id=:companyId and active=true")
                .param("companyId", companyId).query(Long.class).single();
        long interviews = usage(companyId, period);
        long trialDaysRemaining = state.trialEndsAt() == null ? 0 : Math.max(0,
                Duration.between(clock.instant(), state.trialEndsAt()).toDays() + 1);
        return new SubscriptionSnapshot(state.planCode(), state.planName(), state.status(), state.trialEndsAt(),
                trialDaysRemaining, new UsageSnapshot(vacancies, state.maxActiveVacancies(), members,
                state.maxMembers(), interviews, state.maxMonthlyInterviews()), state.features());
    }

    private PlanState require(UUID companyId, String feature) {
        PlanState state = state(companyId).orElseThrow(EntitlementDeniedException::new);
        if (!("ACTIVE".equals(state.status()) || ("TRIALING".equals(state.status()) &&
                state.trialEndsAt() != null && state.trialEndsAt().isAfter(clock.instant()))))
            throw new EntitlementDeniedException();
        if (!Boolean.TRUE.equals(state.features().get(feature))) throw new EntitlementDeniedException();
        return state;
    }

    private Optional<PlanState> state(UUID companyId) {
        String sql = """
                select s.plan_code,p.name,s.status,s.trial_ends_at,p.max_active_vacancies,p.max_members,
                       p.max_monthly_interviews
                  from saas_subscriptions s join saas_plans p on p.code=s.plan_code and p.active=true
                 where s.company_id=:companyId
                """;
        Optional<PlanState> base = jdbc.sql(sql).param("companyId", companyId).query((rs, ignored) -> new PlanState(
                rs.getString("plan_code"), rs.getString("name"), rs.getString("status"),
                rs.getTimestamp("trial_ends_at") == null ? null : rs.getTimestamp("trial_ends_at").toInstant(),
                rs.getInt("max_active_vacancies"), rs.getInt("max_members"),
                rs.getInt("max_monthly_interviews"), new LinkedHashMap<>())).optional();
        base.ifPresent(state -> jdbc.sql("select feature_key,enabled from saas_entitlements where plan_code=:plan")
                .param("plan", state.planCode()).query((rs, ignored) -> Map.entry(rs.getString(1), rs.getBoolean(2)))
                .list().forEach(entry -> state.features().put(entry.getKey(), entry.getValue())));
        return base;
    }

    private long usage(UUID companyId, LocalDate period) {
        return jdbc.sql("select quantity from saas_usage where company_id=:companyId and metric='MONTHLY_INTERVIEWS' and period_start=:period")
                .param("companyId", companyId).param("period", period).query(Long.class).optional().orElse(0L);
    }
    private void lock(UUID companyId) {
        jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtext(cast(:companyId as text)))) lock_row")
                .param("companyId", companyId).query(Long.class).single();
    }

    private record PlanState(String planCode, String planName, String status, Instant trialEndsAt,
                             int maxActiveVacancies, int maxMembers, int maxMonthlyInterviews,
                             Map<String,Boolean> features) { }
    public record SubscriptionSnapshot(String planCode, String planName, String status, Instant trialEndsAt,
                                       long trialDaysRemaining, UsageSnapshot usage, Map<String,Boolean> entitlements) { }
    public record UsageSnapshot(long activeVacancies, long activeVacanciesLimit, long members, long membersLimit,
                                long monthlyInterviews, long monthlyInterviewsLimit) { }
    public static final class EntitlementDeniedException extends IllegalStateException {
        public EntitlementDeniedException() { super("Este recurso não está disponível na assinatura atual. Seus dados continuam acessíveis."); }
    }
    public static final class QuotaExceededException extends IllegalStateException {
        public QuotaExceededException(String metric, long current, long limit) {
            super("Limite de " + metric.toLowerCase(Locale.ROOT) + " atingido (" + current + " de " + limit + ").");
        }
    }
}
