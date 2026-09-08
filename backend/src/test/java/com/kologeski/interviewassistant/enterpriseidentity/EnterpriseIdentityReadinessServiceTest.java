package com.kologeski.interviewassistant.enterpriseidentity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnterpriseIdentityReadinessServiceTest {
    @Test
    void remainsFailClosedWithDefaultConfiguration() {
        var service = new EnterpriseIdentityReadinessService(new EnterpriseIdentityProperties(), List.of());

        var readiness = service.readiness();

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.blockers()).extracting(EnterpriseIdentityReadinessService.Blocker::code)
                .containsExactly("FEATURE_DISABLED", "PROVIDER_DECISION_PENDING", "PROTOCOL_NOT_SELECTED",
                        "DOMAIN_NOT_VERIFIED", "CLAIM_CONTRACT_INCOMPLETE", "RECOVERY_POLICY_PENDING",
                        "ADAPTER_UNAVAILABLE");
        assertThatThrownBy(service::requireReady)
                .isInstanceOf(EnterpriseIdentityReadinessService.EnterpriseIdentityUnavailableException.class);
    }

    @Test
    void isReadyOnlyWithEveryGateAndMatchingConfiguredAdapter() {
        var properties = completeProperties();
        EnterpriseIdentityProviderPort adapter = new EnterpriseIdentityProviderPort() {
            public String providerKey() { return "approved-idp"; }
            public EnterpriseIdentityProtocol protocol() { return EnterpriseIdentityProtocol.OIDC; }
            public boolean configured() { return true; }
        };

        var readiness = new EnterpriseIdentityReadinessService(properties, List.of(adapter)).readiness();

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockers()).isEmpty();
    }

    @Test
    void rejectsAnAdapterForAnotherProvider() {
        var properties = completeProperties();
        EnterpriseIdentityProviderPort adapter = new EnterpriseIdentityProviderPort() {
            public String providerKey() { return "unapproved-idp"; }
            public EnterpriseIdentityProtocol protocol() { return EnterpriseIdentityProtocol.OIDC; }
            public boolean configured() { return true; }
        };

        assertThat(new EnterpriseIdentityReadinessService(properties, List.of(adapter)).readiness().blockers())
                .extracting(EnterpriseIdentityReadinessService.Blocker::code).containsExactly("ADAPTER_UNAVAILABLE");
    }

    private EnterpriseIdentityProperties completeProperties() {
        var properties = new EnterpriseIdentityProperties();
        properties.setEnabled(true);
        properties.setProviderApproved(true);
        properties.setRecoveryPolicyApproved(true);
        properties.setProtocol(EnterpriseIdentityProtocol.OIDC);
        properties.setProviderKey("approved-idp");
        properties.setIssuer("https://identity.example.test");
        properties.setAudience("interview-assistant");
        properties.setVerifiedDomains(List.of("example.test"));
        return properties;
    }
}
