package edu.university.badminton.dto;

import edu.university.badminton.model.SessionType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Result DTO when attempting to admit the next student in the queue line.
 */
public record AdmitNextResponseDto(
        Long queueId,
        LocalDate sessionDate,
        SessionType sessionType,
        boolean studentAdmitted,
        Long queueEntryId,
        Long studentId,
        String studentName,
        Instant admittedAt,
        Instant expiresAt,
        long remainingSeconds,
        long waitingRemaining,
        String message
) {}
