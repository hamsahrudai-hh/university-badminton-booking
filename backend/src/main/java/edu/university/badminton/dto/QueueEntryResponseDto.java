package edu.university.badminton.dto;

import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.QueueEntryStatus;
import edu.university.badminton.model.SessionType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Detailed representation of a student's queue entry, position, and active booking opportunity.
 */
public record QueueEntryResponseDto(
        Long queueEntryId,
        Long queueId,
        Long studentId,
        String studentName,
        LocalDate sessionDate,
        SessionType sessionType,
        QueueEntryStatus status,
        Long positionInLine,
        Instant joinedAt,
        Instant admittedAt,
        Instant expiresAt,
        Long remainingSeconds,
        String priorityTier,
        String message
) {
    public QueueEntryResponseDto(
            Long queueEntryId,
            Long queueId,
            Long studentId,
            String studentName,
            LocalDate sessionDate,
            SessionType sessionType,
            QueueEntryStatus status,
            Long positionInLine,
            Instant joinedAt,
            Instant admittedAt,
            Instant expiresAt,
            Long remainingSeconds,
            String message
    ) {
        this(queueEntryId, queueId, studentId, studentName, sessionDate, sessionType, status, positionInLine,
                joinedAt, admittedAt, expiresAt, remainingSeconds, "STANDARD", message);
    }

    public static QueueEntryResponseDto fromEntity(QueueEntry entry, Long position, Instant currentTime, String message) {
        Long remaining = null;
        if (entry.getStatus() == QueueEntryStatus.ADMITTED && entry.getExpiresAt() != null && currentTime != null) {
            remaining = Math.max(0, entry.getExpiresAt().getEpochSecond() - currentTime.getEpochSecond());
        }
        Integer credits = entry.getStudent() != null ? entry.getStudent().getWeeklyCredits() : null;
        String tier = (credits != null && credits > 0) ? "STANDARD" : "REDUCED";
        return new QueueEntryResponseDto(
                entry.getId(),
                entry.getQueue().getId(),
                entry.getStudent().getId(),
                entry.getStudent().getFullName(),
                entry.getQueue().getSessionDate(),
                entry.getQueue().getSessionType(),
                entry.getStatus(),
                position,
                entry.getJoinedAt(),
                entry.getAdmittedAt(),
                entry.getExpiresAt(),
                remaining,
                tier,
                message
        );
    }
}
