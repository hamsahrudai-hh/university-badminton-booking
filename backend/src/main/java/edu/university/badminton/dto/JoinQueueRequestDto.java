package edu.university.badminton.dto;

import edu.university.badminton.model.SessionType;

import java.time.LocalDate;

/**
 * Request payload for a student joining a booking queue.
 * <p>
 * <strong>Important Security Note:</strong> Client-supplied {@code studentId} is a temporary development
 * mechanism until Phase 3 authentication and JWT session validation are implemented.
 * It is not intended as secure client-side authentication.
 */
public record JoinQueueRequestDto(
        Long studentId,
        LocalDate date,
        SessionType sessionType
) {}
