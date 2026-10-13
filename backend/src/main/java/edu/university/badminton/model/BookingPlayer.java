package edu.university.badminton.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Entity representing an individual player assigned to a spot within a booking.
 */
@Entity
@Table(
        name = "booking_players",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_player_spot", columnNames = {"booking_id", "spot_number"}),
                @UniqueConstraint(name = "uk_booking_player_student", columnNames = {"booking_id", "student_id"}),
                @UniqueConstraint(name = "uk_booking_player_qr_token", columnNames = {"qr_token"})
        }
)
public class BookingPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "spot_number", nullable = false)
    private Integer spotNumber;

    @Column(name = "qr_token", nullable = false, unique = true)
    private String qrToken;

    @Column(name = "checked_in", nullable = false)
    private Boolean checkedIn = false;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by", length = 100)
    private String checkedInBy;

    @Column(name = "no_show", nullable = false)
    private Boolean noShow = false;

    @Column(name = "no_show_processed_at")
    private Instant noShowProcessedAt;

    @Column(name = "credits_deducted", nullable = false)
    private Integer creditsDeducted = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BookingPlayer() {
    }

    public BookingPlayer(Booking booking, Student student, Integer spotNumber, String qrToken) {
        this.booking = booking;
        this.student = student;
        this.spotNumber = spotNumber;
        this.qrToken = qrToken;
        this.checkedIn = false;
        this.noShow = false;
        this.creditsDeducted = 1;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.checkedIn == null) {
            this.checkedIn = false;
        }
        if (this.noShow == null) {
            this.noShow = false;
        }
        if (this.creditsDeducted == null) {
            this.creditsDeducted = 1;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Integer getSpotNumber() {
        return spotNumber;
    }

    public void setSpotNumber(Integer spotNumber) {
        this.spotNumber = spotNumber;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public Boolean getCheckedIn() {
        return checkedIn;
    }

    public void setCheckedIn(Boolean checkedIn) {
        this.checkedIn = checkedIn;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(Instant checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public String getCheckedInBy() {
        return checkedInBy;
    }

    public void setCheckedInBy(String checkedInBy) {
        this.checkedInBy = checkedInBy;
    }

    public Boolean getNoShow() {
        return noShow;
    }

    public void setNoShow(Boolean noShow) {
        this.noShow = noShow;
    }

    public Instant getNoShowProcessedAt() {
        return noShowProcessedAt;
    }

    public void setNoShowProcessedAt(Instant noShowProcessedAt) {
        this.noShowProcessedAt = noShowProcessedAt;
    }

    public Integer getCreditsDeducted() {
        return creditsDeducted;
    }

    public void setCreditsDeducted(Integer creditsDeducted) {
        this.creditsDeducted = creditsDeducted;
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
        BookingPlayer that = (BookingPlayer) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
