package edu.university.badminton.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Entity representing recurring session configuration and queue rules for morning/evening sessions.
 */
@Entity
@Table(name = "session_configs")
public class SessionConfig {

    public static final int DEFAULT_SLOT_DURATION_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, unique = true)
    private SessionType sessionType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes = DEFAULT_SLOT_DURATION_MINUTES;

    /**
     * Time when the booking queue opens (e.g. 21:00 for morning sessions).
     */
    @Column(name = "queue_open_time", nullable = false)
    private LocalTime queueOpenTime;

    /**
     * Time when the booking admissions actually begin (e.g. 21:05 for morning sessions).
     */
    @Column(name = "booking_start_time", nullable = false)
    private LocalTime bookingStartTime;

    /**
     * How many days prior the queue opens (e.g. 1 = previous day, 0 = same day).
     */
    @Column(name = "queue_days_in_advance", nullable = false)
    private Integer queueDaysInAdvance = 1;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SessionConfig() {
    }

    public SessionConfig(SessionType sessionType,
                         LocalTime startTime,
                         LocalTime endTime,
                         Integer slotDurationMinutes,
                         LocalTime queueOpenTime,
                         LocalTime bookingStartTime,
                         Integer queueDaysInAdvance) {
        validateSessionConfig(startTime, endTime, slotDurationMinutes);
        this.sessionType = sessionType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotDurationMinutes = slotDurationMinutes != null ? slotDurationMinutes : DEFAULT_SLOT_DURATION_MINUTES;
        this.queueOpenTime = queueOpenTime;
        this.bookingStartTime = bookingStartTime;
        this.queueDaysInAdvance = queueDaysInAdvance != null ? queueDaysInAdvance : 1;
        this.active = true;
    }

    private static void validateSessionConfig(LocalTime start, LocalTime end, Integer durationMinutes) {
        if (start != null && end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("SessionConfig startTime must be strictly before endTime.");
        }
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new IllegalArgumentException("SessionConfig slotDurationMinutes must be positive.");
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.slotDurationMinutes == null) {
            this.slotDurationMinutes = DEFAULT_SLOT_DURATION_MINUTES;
        }
        if (this.queueDaysInAdvance == null) {
            this.queueDaysInAdvance = 1;
        }
        if (this.active == null) {
            this.active = true;
        }
        validateSessionConfig(this.startTime, this.endTime, this.slotDurationMinutes);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(SessionType sessionType) {
        this.sessionType = sessionType;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getSlotDurationMinutes() {
        return slotDurationMinutes;
    }

    public void setSlotDurationMinutes(Integer slotDurationMinutes) {
        this.slotDurationMinutes = slotDurationMinutes;
    }

    public LocalTime getQueueOpenTime() {
        return queueOpenTime;
    }

    public void setQueueOpenTime(LocalTime queueOpenTime) {
        this.queueOpenTime = queueOpenTime;
    }

    public LocalTime getBookingStartTime() {
        return bookingStartTime;
    }

    public void setBookingStartTime(LocalTime bookingStartTime) {
        this.bookingStartTime = bookingStartTime;
    }

    public Integer getQueueDaysInAdvance() {
        return queueDaysInAdvance;
    }

    public void setQueueDaysInAdvance(Integer queueDaysInAdvance) {
        this.queueDaysInAdvance = queueDaysInAdvance;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionConfig that = (SessionConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
