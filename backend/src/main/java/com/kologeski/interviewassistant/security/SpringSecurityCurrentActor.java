package com.kologeski.interviewassistant.security;

import com.kologeski.interviewassistant.audit.CurrentActor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class SpringSecurityCurrentActor implements CurrentActor {
    @Override
    public AuthenticatedActor required() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("authenticated user is required");
        }
        String id = authentication.getName();
        return new AuthenticatedActor(id, id);
    }
}
