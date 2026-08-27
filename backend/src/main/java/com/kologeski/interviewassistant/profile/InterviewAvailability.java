package com.kologeski.interviewassistant.profile;

import com.kologeski.interviewassistant.user.User;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "interview_availability")
public class InterviewAvailability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12)
    private DayOfWeek dayOfWeek;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InterviewAvailability() { }

    InterviewAvailability(User user, DayOfWeek day, LocalTime start, LocalTime end, Instant now) {
        this.user = user;
        update(day, start, end, now);
        this.createdAt = now;
    }

    void update(DayOfWeek day, LocalTime start, LocalTime end, Instant now) {
        this.dayOfWeek = day;
        this.startTime = start;
        this.endTime = end;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
