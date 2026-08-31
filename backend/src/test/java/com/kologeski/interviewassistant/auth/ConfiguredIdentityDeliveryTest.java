package com.kologeski.interviewassistant.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredIdentityDeliveryTest {
    @Test void doesNotExposeIdentityTokensByDefault() {
        var receipt = new ConfiguredIdentityDelivery(false)
                .deliver(IdentityToken.Purpose.PASSWORD_RESET, "user@example.test", "secret-token");
        assertEquals("PROVIDER_NOT_CONFIGURED", receipt.status());
        assertNull(receipt.developmentPath());
    }

    @Test void exposesOnlyAnExplicitDevelopmentPath() {
        var receipt = new ConfiguredIdentityDelivery(true)
                .deliver(IdentityToken.Purpose.EMAIL_VERIFICATION, "user@example.test", "secret-token");
        assertEquals("DEVELOPMENT_LINK", receipt.status());
        assertEquals("/verify-email/secret-token", receipt.developmentPath());
    }
}
