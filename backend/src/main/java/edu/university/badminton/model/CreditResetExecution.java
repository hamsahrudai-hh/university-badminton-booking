package edu.university.badminton.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit record of an executed weekly student-credit reset.
 * Tracks the week period key (e.g., "2026-W42") to guarantee restart-safe idempotency.
 */
@Entity
@Table(
        name = "credit_reset_executions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_credit_reset_period_key", columnNames = {"period_key"})
        }
)
public class CreditResetExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_key", nullable = false, unique = true, length = 30)
    private String periodKey;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "students_reset_count", nullable = false)
    private int studentsResetCount;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "notes", length = 255)
    private String notes;

    public CreditResetExecution() {
    }

    public CreditResetExecution(String periodKey, Instant executedAt, int studentsResetCount, String triggeredBy, String status, String notes) {
        this.periodKey = periodKey;
        this.executedAt = executedAt != null ? executedAt : Instant.now();
        this.studentsResetCount = studentsResetCount;
        this.triggeredBy = triggeredBy;
        this.status = status != null ? status : "SUCCESS";
        this.notes = notes;
    }

    @PrePersist
    protected void onCreate() {
        if (this.executedAt == null) {
            this.executedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = "SUCCESS";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public int getStudentsResetCount() {
        return studentsResetCount;
    }

    public void setStudentsResetCount(int studentsResetCount) {
        this.studentsResetCount = studentsResetCount;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreditResetExecution that = (CreditResetExecution) o;
        return Objects.equals(id, that.id) || (periodKey != null && Objects.equals(periodKey, that.periodKey));
    }

    @Override
    public int hashCode() {
        return Objects.hash(periodKey != null ? periodKey : id);
    }
}
