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
import java.util.Objects;

/**
 * Entity representing an individual student's entry and opportunity lifecycle within a booking queue.
 */
@Entity
@Table(
        name = "queue_entries",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_queue_student", columnNames = {"queue_id", "student_id"})
        }
)
public class QueueEntry {

    public static final int OPPORTUNITY_DURATION_SECONDS = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private BookingQueue queue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QueueEntryStatus status = QueueEntryStatus.WAITING;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "admitted_at")
    private Instant admittedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public QueueEntry() {
    }

    public QueueEntry(BookingQueue queue, Student student, Instant joinedAt) {
        this.queue = queue;
        this.student = student;
        this.joinedAt = joinedAt;
        this.status = QueueEntryStatus.WAITING;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = QueueEntryStatus.WAITING;
        }
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }

    /**
     * Admits the student to the active 60-second booking opportunity.
     *
     * @param admissionTime the server timestamp of admission
     */
    public void admit(Instant admissionTime) {
        if (admissionTime == null) {
            throw new IllegalArgumentException("Admission timestamp cannot be null.");
        }
        this.status = QueueEntryStatus.ADMITTED;
        this.admittedAt = admissionTime;
        this.expiresAt = admissionTime.plusSeconds(OPPORTUNITY_DURATION_SECONDS);
    }

    /**
     * Marks the opportunity as expired.
     */
    public void expire() {
        this.status = QueueEntryStatus.EXPIRED;
    }

    /**
     * Marks the opportunity as completed after successful booking.
     */
    public void complete() {
        this.status = QueueEntryStatus.COMPLETED;
    }

    /**
     * Checks whether this entry currently holds an active, unexpired booking opportunity.
     *
     * @param currentTime the current server timestamp
     * @return true if currently admitted and not yet expired
     */
    public boolean isOpportunityActive(Instant currentTime) {
        if (this.status != QueueEntryStatus.ADMITTED || this.expiresAt == null || currentTime == null) {
            return false;
        }
        return currentTime.isBefore(this.expiresAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BookingQueue getQueue() {
        return queue;
    }

    public void setQueue(BookingQueue queue) {
        this.queue = queue;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public QueueEntryStatus getStatus() {
        return status;
    }

    public void setStatus(QueueEntryStatus status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getAdmittedAt() {
        return admittedAt;
    }

    public void setAdmittedAt(Instant admittedAt) {
        this.admittedAt = admittedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
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
        QueueEntry that = (QueueEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
