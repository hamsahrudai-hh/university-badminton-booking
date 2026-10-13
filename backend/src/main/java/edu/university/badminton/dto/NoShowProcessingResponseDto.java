package edu.university.badminton.dto;

import java.util.List;

/**
 * Summary response returned after executing no-show processing for expired slots.
 */
public record NoShowProcessingResponseDto(
        int processedCount,
        List<PenalizedPlayerDto> penalizedPlayers,
        String message
) {
}
