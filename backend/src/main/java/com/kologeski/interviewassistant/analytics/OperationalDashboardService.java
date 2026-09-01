package com.kologeski.interviewassistant.analytics;

import com.kologeski.interviewassistant.api.PageResponse;
import com.kologeski.interviewassistant.company.CompanyAuthorizationPolicy;
import com.kologeski.interviewassistant.company.CompanyCapability;
import com.kologeski.interviewassistant.interview.InterviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class OperationalDashboardService {
    private final JdbcClient jdbc;
    private final CompanyAuthorizationPolicy authorization;
    private final Clock clock;

    public OperationalDashboardService(JdbcClient jdbc, CompanyAuthorizationPolicy authorization, Clock clock) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(LocalDate from, LocalDate to, InterviewStatus status, UUID vacancyId,
                                       Pageable vacancyPage, Pageable interviewerPage) {
        var active = authorization.require(CompanyCapability.VIEW_INTERVIEWS);
        Period period = period(from, to);
        Filter filter = new Filter(active.companyId(), period.from(), period.toExclusive(), status, vacancyId);
        Summary summary = summary(filter);
        List<StatusMetric> statuses = statusMetrics(filter);
        PageResponse<VacancyMetric> vacancies = vacancyMetrics(filter, vacancyPage);
        PageResponse<InterviewerMetric> interviewers = interviewerMetrics(filter, interviewerPage);
        return new DashboardResponse(period.from().atZone(ZoneOffset.UTC).toLocalDate(),
                period.toExclusive().minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate(), summary,
                statuses, vacancies, interviewers);
    }

    private Summary summary(Filter filter) {
        String sql = """
                select count(*) total,
                       count(*) filter (where status = 'IN_PROGRESS') in_progress,
                       count(*) filter (where status = 'COMPLETED') completed,
                       coalesce(round(avg(extract(epoch from (completed_at - started_at)) / 60.0)
                           filter (where completed_at is not null and started_at is not null), 1), 0) average_minutes
                  from interviews
                """ + where(filter);
        return bind(jdbc.sql(sql), filter).query((rs, ignored) -> {
            long total = rs.getLong("total");
            long completed = rs.getLong("completed");
            double completionRate = total == 0 ? 0 : Math.round(completed * 10_000.0 / total) / 100.0;
            return new Summary(total, rs.getLong("in_progress"), completed,
                    rs.getBigDecimal("average_minutes"), completionRate);
        }).single();
    }

    private List<StatusMetric> statusMetrics(Filter filter) {
        String sql = "select status, count(*) total from interviews " + where(filter) + " group by status order by status";
        return bind(jdbc.sql(sql), filter).query((rs, ignored) ->
                new StatusMetric(InterviewStatus.valueOf(rs.getString("status")), rs.getLong("total"))).list();
    }

    private PageResponse<VacancyMetric> vacancyMetrics(Filter filter, Pageable pageable) {
        String grouped = " from interviews " + where(filter) + " group by vacancy_id, vacancy_title_snapshot ";
        long total = bind(jdbc.sql("select count(*) from (select vacancy_id" + grouped + ") metrics"), filter)
                .query(Long.class).single();
        String sql = "select vacancy_id, vacancy_title_snapshot, count(*) total, " +
                "count(*) filter (where status='COMPLETED') completed" + grouped +
                "order by total desc, vacancy_title_snapshot asc limit :limit offset :offset";
        List<VacancyMetric> content = bind(jdbc.sql(sql), filter).param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset()).query((rs, ignored) -> new VacancyMetric(
                        rs.getObject("vacancy_id", UUID.class), rs.getString("vacancy_title_snapshot"),
                        rs.getLong("total"), rs.getLong("completed"))).list();
        return page(content, pageable, total);
    }

    private PageResponse<InterviewerMetric> interviewerMetrics(Filter filter, Pageable pageable) {
        String cte = "with grouped as (select interviewer_id, count(*) total, " +
                "count(*) filter (where status='COMPLETED') completed from interviews " + where(filter) +
                " and interviewer_id is not null group by interviewer_id), normalized as (" +
                "select case when total < 3 then 'SUPPRESSED' else interviewer_id end interviewer_code, " +
                "sum(total) total, sum(completed) completed from grouped " +
                "group by case when total < 3 then 'SUPPRESSED' else interviewer_id end) ";
        long total = bind(jdbc.sql(cte + "select count(*) from normalized"), filter).query(Long.class).single();
        List<InterviewerMetric> content = bind(jdbc.sql(cte + "select interviewer_code,total,completed from normalized " +
                        "order by total desc, interviewer_code asc limit :limit offset :offset"), filter)
                .param("limit", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, ignored) -> {
                    String code = rs.getString("interviewer_code");
                    return new InterviewerMetric(code, "SUPPRESSED".equals(code)
                            ? "Outros (grupos pequenos)" : "Entrevistador #" + code,
                            rs.getLong("total"), rs.getLong("completed"));
                }).list();
        return page(content, pageable, total);
    }

    private String where(Filter filter) {
        return " where company_id=:companyId and created_at>=:from and created_at<:to" +
                (filter.status() == null ? "" : " and status=:status") +
                (filter.vacancyId() == null ? "" : " and vacancy_id=:vacancyId");
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Filter filter) {
        spec = spec.param("companyId", filter.companyId()).param("from", Timestamp.from(filter.from()))
                .param("to", Timestamp.from(filter.toExclusive()));
        if (filter.status() != null) spec = spec.param("status", filter.status().name());
        if (filter.vacancyId() != null) spec = spec.param("vacancyId", filter.vacancyId());
        return spec;
    }

    private Period period(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = requestedTo == null ? today : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) throw new IllegalArgumentException("A data inicial deve ser anterior à data final.");
        if (from.isBefore(to.minusDays(365))) throw new IllegalArgumentException("O período máximo é de 366 dias.");
        return new Period(from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private <T> PageResponse<T> page(List<T> content, Pageable pageable, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= totalPages);
    }

    private record Filter(UUID companyId, Instant from, Instant toExclusive, InterviewStatus status, UUID vacancyId) { }
    private record Period(Instant from, Instant toExclusive) { }
    public record DashboardResponse(LocalDate from, LocalDate to, Summary summary, List<StatusMetric> byStatus,
                                    PageResponse<VacancyMetric> byVacancy,
                                    PageResponse<InterviewerMetric> byInterviewer) { }
    public record Summary(long total, long inProgress, long completed, java.math.BigDecimal averageMinutes,
                          double completionRate) { }
    public record StatusMetric(InterviewStatus status, long total) { }
    public record VacancyMetric(UUID vacancyId, String vacancyTitle, long total, long completed) { }
    public record InterviewerMetric(String interviewerCode, String label, long total, long completed) { }
}
