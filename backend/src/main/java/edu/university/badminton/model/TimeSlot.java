package edu.university.badminton.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a concrete 30-minute dated court booking slot.
 */
@Entity
@Table(
        name = "time_slots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_court_slot_time", columnNames = {"court_id", "start_time"})
        }
)
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_config_id", nullable = false)
    private SessionConfig sessionConfig;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TimeSlot() {
    }

    public TimeSlot(Court court, SessionConfig sessionConfig, LocalDate slotDate, LocalDateTime startTime, LocalDateTime endTime) {
        validateSlotTimes(startTime, endTime);
        this.court = court;
        this.sessionConfig = sessionConfig;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SlotStatus.AVAILABLE;
    }

    public TimeSlot(Court court, SessionConfig sessionConfig, LocalDate slotDate, LocalDateTime startTime, LocalDateTime endTime, SlotStatus status) {
        validateSlotTimes(startTime, endTime);
        this.court = court;
        this.sessionConfig = sessionConfig;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status != null ? status : SlotStatus.AVAILABLE;
    }

    private static void validateSlotTimes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("TimeSlot startTime and endTime cannot be null.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("TimeSlot endTime must be strictly after startTime (cannot be zero-length or inverted).");
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = SlotStatus.AVAILABLE;
        }
        validateSlotTimes(this.startTime, this.endTime);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Court getCourt() {
        return court;
    }

    public void setCourt(Court court) {
        this.court = court;
    }

    public SessionConfig getSessionConfig() {
        return sessionConfig;
    }

    public void setSessionConfig(SessionConfig sessionConfig) {
        this.sessionConfig = sessionConfig;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
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
        TimeSlot timeSlot = (TimeSlot) o;
        return Objects.equals(id, timeSlot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
