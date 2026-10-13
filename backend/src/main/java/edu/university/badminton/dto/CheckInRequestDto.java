package edu.university.badminton.dto;

/**
 * Request payload for staff player QR check-in.
 */
public record CheckInRequestDto(
        String qrToken,
        String staffIdentifier
) {
}
