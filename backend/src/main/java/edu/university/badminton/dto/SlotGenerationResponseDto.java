package edu.university.badminton.dto;

import java.time.LocalDate;

/**
 * Result of time slot generation for a given calendar date.
 */
public record SlotGenerationResponseDto(
        LocalDate date,
        int totalSlots,
        int createdSlots,
        int existingSlots,
        String message
) {}
