package com.kologeski.interviewassistant.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class MutableClock extends Clock {
    private Instant instant;

    public MutableClock(Instant instant) { this.instant = instant; }
    public void advanceTo(Instant value) { instant = value; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
}
