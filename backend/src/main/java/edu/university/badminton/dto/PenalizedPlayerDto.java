package edu.university.badminton.dto;

import java.time.LocalDateTime;

/**
 * Representation of a player penalized during no-show processing.
 */
public record PenalizedPlayerDto(
        Long bookingPlayerId,
        Long bookingId,
        Long studentId,
        String studentName,
        String admissionNumber,
        Integer spotNumber,
        Integer courtNumber,
        LocalDateTime slotStartTime,
        int forfeitedCredits,
        double updatedReliabilityRating
) {
}
