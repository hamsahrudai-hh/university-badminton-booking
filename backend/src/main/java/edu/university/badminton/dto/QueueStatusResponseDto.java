package edu.university.badminton.dto;

import edu.university.badminton.model.SessionType;

import java.time.Instant;
import java.time.LocalDate;

import java.time.LocalDateTime;

/**
 * Public snapshot of the current state of a booking queue, including counts and active opportunity.
 * <p>
 * <strong>Privacy Protection:</strong> Does not expose student identifiers or personal names to
 * unauthenticated callers.
 */
public record QueueStatusResponseDto(
        Long queueId,
        LocalDate sessionDate,
        SessionType sessionType,
        String queueStatus,
        long totalEntries,
        long waitingCount,
        boolean hasActiveOpportunity,
        Instant activeOpportunityAdmittedAt,
        Instant activeOpportunityExpiresAt,
        Long remainingSeconds,
        LocalDateTime queueOpenDateTime,
        LocalDateTime bookingStartDateTime,
        LocalDateTime sessionEndDateTime,
        Instant serverTime,
        boolean isQueueOpen,
        boolean isBookingOpen,
        String message
) {
    public QueueStatusResponseDto(
            Long queueId,
            LocalDate sessionDate,
            SessionType sessionType,
            String queueStatus,
            long totalEntries,
            long waitingCount,
            boolean hasActiveOpportunity,
            Instant activeOpportunityAdmittedAt,
            Instant activeOpportunityExpiresAt,
            Long remainingSeconds,
            String message
    ) {
        this(queueId, sessionDate, sessionType, queueStatus, totalEntries, waitingCount,
                hasActiveOpportunity, activeOpportunityAdmittedAt, activeOpportunityExpiresAt,
                remainingSeconds, null, null, null, null, false, false, message);
    }
}
