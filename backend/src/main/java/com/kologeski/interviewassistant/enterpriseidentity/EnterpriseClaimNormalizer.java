package com.kologeski.interviewassistant.enterpriseidentity;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EnterpriseClaimNormalizer {
    public NormalizedIdentity normalize(VerifiedProviderAssertion assertion, EnterpriseIdentityProperties properties) {
        requireEqual("issuer", properties.getIssuer(), assertion.issuer());
        if (properties.getAudience() == null || !assertion.audiences().contains(properties.getAudience())) {
            throw new InvalidEnterpriseAssertionException("A audiência da identidade não é confiável.");
        }

        var mapping = properties.getClaims();
        String subject = requiredString(assertion.claims(), mapping.getSubject(), "subject");
        String email = requiredString(assertion.claims(), mapping.getEmail(), "email").trim().toLowerCase(Locale.ROOT);
        if (!Boolean.TRUE.equals(assertion.claims().get(mapping.getEmailVerified()))) {
            throw new InvalidEnterpriseAssertionException("O provedor não confirmou o e-mail.");
        }
        int separator = email.lastIndexOf('@');
        if (separator <= 0 || separator == email.length() - 1) {
            throw new InvalidEnterpriseAssertionException("O e-mail corporativo é inválido.");
        }
        String domain = email.substring(separator + 1);
        boolean trustedDomain = properties.getVerifiedDomains().stream()
                .filter(Objects::nonNull).map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(domain::equals);
        if (!trustedDomain) throw new InvalidEnterpriseAssertionException("O domínio do e-mail não foi verificado.");

        Set<String> groups = stringSet(assertion.claims().get(mapping.getGroups()));
        return new NormalizedIdentity(assertion.issuer(), subject, email, domain, groups);
    }

    private static void requireEqual(String field, String expected, String actual) {
        if (expected == null || expected.isBlank() || !expected.equals(actual)) {
            throw new InvalidEnterpriseAssertionException("O " + field + " da identidade não é confiável.");
        }
    }

    private static String requiredString(Map<String, Object> claims, String name, String label) {
        Object value = name == null ? null : claims.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidEnterpriseAssertionException("A identidade não contém " + label + " válido.");
        }
        return text;
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> values)) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        values.stream().filter(String.class::isInstance).map(String.class::cast)
                .map(String::trim).filter(text -> !text.isBlank()).forEach(result::add);
        return Collections.unmodifiableSet(result);
    }

    /** The adapter may create this value only after protocol-level signature and replay checks. */
    public record VerifiedProviderAssertion(String issuer, Set<String> audiences, Map<String, Object> claims) {
        public VerifiedProviderAssertion {
            audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
            claims = claims == null ? Map.of() : Map.copyOf(claims);
        }
    }

    public record NormalizedIdentity(String issuer, String subject, String email, String verifiedDomain,
                                     Set<String> groups) { }

    public static final class InvalidEnterpriseAssertionException extends RuntimeException {
        public InvalidEnterpriseAssertionException(String message) { super(message); }
    }
}
