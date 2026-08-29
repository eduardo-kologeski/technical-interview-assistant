package com.kologeski.interviewassistant.audit;

public interface CurrentActor {
    AuthenticatedActor required();

    record AuthenticatedActor(String id, String displayName) {
        public AuthenticatedActor {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("actor id is required");
            if (displayName == null || displayName.isBlank()) displayName = id;
        }
    }
}
