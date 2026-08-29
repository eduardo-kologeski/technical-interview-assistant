package com.kologeski.interviewassistant.audit;

public final class MutableActor implements CurrentActor {
    private AuthenticatedActor actor;

    public MutableActor(String id) { use(id); }
    public void use(String id) { actor = new AuthenticatedActor(id, id); }
    @Override public AuthenticatedActor required() { return actor; }
}
