package edu.university.badminton.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity representing a confirmed badminton court reservation.
 */
@Entity
@Table(
        name = "bookings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_time_slot", columnNames = {"time_slot_id"}),
                @UniqueConstraint(name = "uk_booking_idempotency", columnNames = {"idempotency_key"})
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_user_id", nullable = false)
    private Student bookingUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_entry_id")
    private QueueEntry queueEntry;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingPlayer> players = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Booking() {
    }

    public Booking(TimeSlot timeSlot, Student bookingUser, QueueEntry queueEntry, String idempotencyKey) {
        this.timeSlot = timeSlot;
        this.bookingUser = bookingUser;
        this.queueEntry = queueEntry;
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.CONFIRMED;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = BookingStatus.CONFIRMED;
        }
    }

    public void addPlayer(BookingPlayer player) {
        this.players.add(player);
        player.setBooking(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    public Student getBookingUser() {
        return bookingUser;
    }

    public void setBookingUser(Student bookingUser) {
        this.bookingUser = bookingUser;
    }

    public QueueEntry getQueueEntry() {
        return queueEntry;
    }

    public void setQueueEntry(QueueEntry queueEntry) {
        this.queueEntry = queueEntry;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public List<BookingPlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<BookingPlayer> players) {
        this.players = players;
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
        Booking booking = (Booking) o;
        return Objects.equals(id, booking.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
