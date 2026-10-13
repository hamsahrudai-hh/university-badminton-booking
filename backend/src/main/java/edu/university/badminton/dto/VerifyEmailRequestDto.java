package edu.university.badminton.dto;

/**
 * Request payload for verifying a student's university email via token.
 */
public record VerifyEmailRequestDto(
        String token
) {
}
