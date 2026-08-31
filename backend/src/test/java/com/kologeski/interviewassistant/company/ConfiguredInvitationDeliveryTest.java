package com.kologeski.interviewassistant.company;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfiguredInvitationDeliveryTest {
    private final InvitationDelivery.InvitationMessage message = new InvitationDelivery.InvitationMessage(
            UUID.randomUUID(), "interviewer@example.test", "Example", "secret-token", Instant.now());

    @Test
    void doesNotExposeInvitationTokenByDefault() {
        var receipt = new ConfiguredInvitationDelivery(false).deliver(message);

        assertEquals("PROVIDER_NOT_CONFIGURED", receipt.status());
        assertNull(receipt.developmentPath());
    }

    @Test
    void exposesDevelopmentPathOnlyWhenExplicitlyEnabled() {
        var receipt = new ConfiguredInvitationDelivery(true).deliver(message);

        assertEquals("DEVELOPMENT_LINK", receipt.status());
        assertEquals("/invites/secret-token", receipt.developmentPath());
    }
}
