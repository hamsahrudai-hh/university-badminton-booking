package edu.university.badminton.dto;

/**
 * Request payload for activating or deactivating a court.
 */
public record UpdateCourtStatusRequestDto(
        Boolean active
) {
}
