package com.kologeski.interviewassistant.company;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredInvitationDelivery implements InvitationDelivery {
    private final boolean exposeDevelopmentLink;

    public ConfiguredInvitationDelivery(@Value("${app.invitation.expose-development-link:false}") boolean exposeDevelopmentLink) {
        this.exposeDevelopmentLink = exposeDevelopmentLink;
    }

    @Override
    public DeliveryReceipt deliver(InvitationMessage message) {
        String path = exposeDevelopmentLink ? "/invites/" + message.token() : null;
        return new DeliveryReceipt(exposeDevelopmentLink ? "DEVELOPMENT_LINK" : "PROVIDER_NOT_CONFIGURED", path);
    }
}
