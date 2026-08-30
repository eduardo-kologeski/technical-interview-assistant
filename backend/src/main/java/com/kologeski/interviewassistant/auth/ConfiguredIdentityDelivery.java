package com.kologeski.interviewassistant.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredIdentityDelivery implements IdentityDelivery {
    private final boolean exposeDevelopmentLink;
    public ConfiguredIdentityDelivery(@Value("${app.identity.expose-development-link:false}") boolean exposeDevelopmentLink) {
        this.exposeDevelopmentLink = exposeDevelopmentLink;
    }
    @Override public DeliveryReceipt deliver(IdentityToken.Purpose purpose, String email, String rawToken) {
        String prefix = purpose == IdentityToken.Purpose.EMAIL_VERIFICATION ? "/verify-email/" : "/reset-password/";
        return new DeliveryReceipt(exposeDevelopmentLink ? "DEVELOPMENT_LINK" : "PROVIDER_NOT_CONFIGURED",
                exposeDevelopmentLink ? prefix + rawToken : null);
    }
}
