package com.kologeski.interviewassistant.company;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyAuthorizationPolicyTest {
    private final CompanyContext context = mock(CompanyContext.class);
    private final CompanyAuthorizationPolicy policy = new CompanyAuthorizationPolicy(context);

    @Test
    void administratorHasEveryCapability() {
        for (CompanyCapability capability : CompanyCapability.values())
            assertThat(policy.allows(CompanyRole.COMPANY_ADMIN, capability)).isTrue();
    }

    @Test
    void interviewerCanViewAndConductButCannotManage() {
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.VIEW_VACANCIES)).isTrue();
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.VIEW_INTERVIEWS)).isTrue();
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.CONDUCT_ASSIGNED_INTERVIEW)).isTrue();
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.MANAGE_MEMBERS)).isFalse();
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.MANAGE_INVITATIONS)).isFalse();
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.MANAGE_VACANCIES)).isFalse();
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.MANAGE_INTERVIEWS)).isFalse();
        assertThat(policy.allows(CompanyRole.INTERVIEWER, CompanyCapability.VIEW_AUDIT_LOG)).isFalse();
    }

    @Test
    void missingCapabilityFailsClosed() {
        var active = new CompanyContext.ActiveCompany(UUID.randomUUID(), 7L, CompanyRole.INTERVIEWER);
        when(context.required()).thenReturn(active);
        assertThatThrownBy(() -> policy.require(CompanyCapability.MANAGE_MEMBERS))
                .isInstanceOf(CompanyContext.CompanyAccessDeniedException.class);
    }
}
