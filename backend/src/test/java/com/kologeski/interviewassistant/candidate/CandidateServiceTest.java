package com.kologeski.interviewassistant.candidate;

import com.kologeski.interviewassistant.audit.AuditTrailService;
import com.kologeski.interviewassistant.company.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CandidateServiceTest {
    @Test void deduplicatesNamesInsideTheTenantAndCreatesOneCandidateEvent() {
        var candidates = mock(CandidateRepository.class); var links = mock(CandidateVacancyRepository.class);
        var events = mock(CandidateTimelineRepository.class); var authorization = mock(CompanyAuthorizationPolicy.class);
        var audit = mock(AuditTrailService.class); var saved = new AtomicReference<Candidate>();
        UUID company = UUID.randomUUID(), vacancy = UUID.randomUUID();
        when(candidates.findByCompanyIdAndNormalizedName(company, "ana silva"))
                .thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(candidates.saveAndFlush(any())).thenAnswer(call -> { Candidate value=call.getArgument(0); saved.set(value); return value; });
        var service = new CandidateService(candidates, links, events, authorization, audit,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));

        Candidate first = service.resolve(company, vacancy, " Ana   Silva ", "42");
        Candidate second = service.resolve(company, vacancy, "ANA SILVA", "42");

        assertThat(second.id()).isEqualTo(first.id());
        verify(candidates).saveAndFlush(any());
        verify(events).save(any(CandidateTimelineEvent.class));
        verify(audit).record(company, 42L, "DOMAIN", "CANDIDATE_CREATED", "CANDIDATE", first.id());
    }

    @Test void neverReturnsACandidateFromAnotherTenant() {
        var candidates = mock(CandidateRepository.class); var authorization = mock(CompanyAuthorizationPolicy.class);
        UUID company = UUID.randomUUID(), id = UUID.randomUUID();
        when(authorization.require(CompanyCapability.VIEW_INTERVIEWS))
                .thenReturn(new CompanyContext.ActiveCompany(company, 7L, CompanyRole.COMPANY_ADMIN));
        var service = new CandidateService(candidates, mock(CandidateVacancyRepository.class),
                mock(CandidateTimelineRepository.class), authorization, mock(AuditTrailService.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(com.kologeski.interviewassistant.api.ResourceNotFoundException.class);
        verify(candidates).findByIdAndCompanyId(id, company);
    }
}
