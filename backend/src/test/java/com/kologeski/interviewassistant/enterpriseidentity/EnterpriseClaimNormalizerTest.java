package com.kologeski.interviewassistant.enterpriseidentity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnterpriseClaimNormalizerTest {
    private final EnterpriseClaimNormalizer normalizer = new EnterpriseClaimNormalizer();
    private EnterpriseIdentityProperties properties;

    @BeforeEach
    void configureContract() {
        properties = new EnterpriseIdentityProperties();
        properties.setIssuer("https://identity.example.test");
        properties.setAudience("interview-assistant");
        properties.setVerifiedDomains(List.of("Example.Test"));
    }

    @Test
    void normalizesOnlyVerifiedClaimsFromTrustedIssuerAudienceAndDomain() {
        var assertion = assertion(Map.of(
                "sub", "external-42", "email", "Admin@Example.Test", "email_verified", true,
                "groups", List.of("interviewers", " hiring-admins ")));

        var identity = normalizer.normalize(assertion, properties);

        assertThat(identity.subject()).isEqualTo("external-42");
        assertThat(identity.email()).isEqualTo("admin@example.test");
        assertThat(identity.verifiedDomain()).isEqualTo("example.test");
        assertThat(identity.groups()).containsExactly("interviewers", "hiring-admins");
    }

    @Test
    void failsClosedForUnverifiedEmailOrDomain() {
        assertThatThrownBy(() -> normalizer.normalize(assertion(Map.of(
                "sub", "42", "email", "admin@example.test", "email_verified", false)), properties))
                .isInstanceOf(EnterpriseClaimNormalizer.InvalidEnterpriseAssertionException.class)
                .hasMessageContaining("não confirmou");
        assertThatThrownBy(() -> normalizer.normalize(assertion(Map.of(
                "sub", "42", "email", "admin@attacker.test", "email_verified", true)), properties))
                .isInstanceOf(EnterpriseClaimNormalizer.InvalidEnterpriseAssertionException.class)
                .hasMessageContaining("não foi verificado");
    }

    @Test
    void rejectsIssuerAndAudienceMismatch() {
        var wrongIssuer = new EnterpriseClaimNormalizer.VerifiedProviderAssertion(
                "https://attacker.test", Set.of("interview-assistant"), Map.of());
        assertThatThrownBy(() -> normalizer.normalize(wrongIssuer, properties)).hasMessageContaining("issuer");

        var wrongAudience = new EnterpriseClaimNormalizer.VerifiedProviderAssertion(
                "https://identity.example.test", Set.of("another-app"), Map.of());
        assertThatThrownBy(() -> normalizer.normalize(wrongAudience, properties)).hasMessageContaining("audiência");
    }

    private EnterpriseClaimNormalizer.VerifiedProviderAssertion assertion(Map<String, Object> claims) {
        return new EnterpriseClaimNormalizer.VerifiedProviderAssertion(
                "https://identity.example.test", Set.of("interview-assistant"), claims);
    }
}
