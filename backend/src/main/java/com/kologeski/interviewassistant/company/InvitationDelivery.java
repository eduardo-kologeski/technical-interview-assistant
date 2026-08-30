package com.kologeski.interviewassistant.company;

import java.time.Instant;
import java.util.UUID;

public interface InvitationDelivery {
    DeliveryReceipt deliver(InvitationMessage message);

    record InvitationMessage(UUID invitationId, String email, String companyName, String token, Instant expiresAt) { }
    record DeliveryReceipt(String status, String developmentPath) { }
}
