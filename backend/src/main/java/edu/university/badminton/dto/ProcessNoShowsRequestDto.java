package edu.university.badminton.dto;

/**
 * Optional request filter for processing no-shows.
 */
public record ProcessNoShowsRequestDto(
        Long timeSlotId
) {
}
