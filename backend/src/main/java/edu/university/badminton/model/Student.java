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
import java.util.Objects;

/**
 * Entity representing a university student or portal user.
 */
@Entity
@Table(name = "students")
public class Student {

    /**
     * Default starting weekly credits per AI_RULES.md.
     */
    public static final int DEFAULT_WEEKLY_CREDITS = 6;

    /**
     * Default starting reliability rating (100.0% reliability on a 0.0 - 100.0 scale).
     * No-shows reduce this rating per attendance tracking rules.
     */
    public static final double DEFAULT_RELIABILITY_RATING = 100.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "university_email", nullable = false, unique = true)
    private String universityEmail;

    @Column(name = "admission_number", nullable = false, unique = true)
    private String admissionNumber;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role = Role.STUDENT;

    @Column(name = "weekly_credits", nullable = false)
    private Integer weeklyCredits = DEFAULT_WEEKLY_CREDITS;

    @Column(name = "reliability_rating", nullable = false)
    private Double reliabilityRating = DEFAULT_RELIABILITY_RATING;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Student() {
    }

    public Student(String fullName, String universityEmail, String admissionNumber, Role role) {
        this.fullName = fullName;
        this.universityEmail = universityEmail;
        this.admissionNumber = admissionNumber;
        this.role = role != null ? role : Role.STUDENT;
        this.weeklyCredits = DEFAULT_WEEKLY_CREDITS;
        this.reliabilityRating = DEFAULT_RELIABILITY_RATING;
        this.active = true;
        this.emailVerified = true;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.weeklyCredits == null) {
            this.weeklyCredits = DEFAULT_WEEKLY_CREDITS;
        }
        if (this.reliabilityRating == null) {
            this.reliabilityRating = DEFAULT_RELIABILITY_RATING;
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.emailVerified == null) {
            this.emailVerified = false;
        }
        if (this.role == null) {
            this.role = Role.STUDENT;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUniversityEmail() {
        return universityEmail;
    }

    public void setUniversityEmail(String universityEmail) {
        this.universityEmail = universityEmail;
    }

    public String getAdmissionNumber() {
        return admissionNumber;
    }

    public void setAdmissionNumber(String admissionNumber) {
        this.admissionNumber = admissionNumber;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Integer getWeeklyCredits() {
        return weeklyCredits;
    }

    public void setWeeklyCredits(Integer weeklyCredits) {
        this.weeklyCredits = weeklyCredits;
    }

    public Double getReliabilityRating() {
        return reliabilityRating;
    }

    public void setReliabilityRating(Double reliabilityRating) {
        this.reliabilityRating = reliabilityRating;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
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
        Student student = (Student) o;
        return Objects.equals(id, student.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
