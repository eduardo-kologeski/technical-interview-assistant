package com.kologeski.interviewassistant.enterpriseidentity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseIdentityAuditTest {
    @Test
    void fingerprintsSubjectsWithoutPersistingTheIdentifier() {
        var audit = new EnterpriseIdentityAudit();

        String fingerprint = audit.fingerprint("sensitive-provider-subject");

        assertThat(fingerprint).hasSize(24).doesNotContain("sensitive-provider-subject");
        assertThat(audit.fingerprint("sensitive-provider-subject")).isEqualTo(fingerprint);
        assertThat(audit.fingerprint(null)).isEqualTo("unknown");
    }
}
