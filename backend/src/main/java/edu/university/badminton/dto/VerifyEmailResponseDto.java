package edu.university.badminton.dto;

/**
 * Public response returned after a student successfully verifies their university email.
 */
public record VerifyEmailResponseDto(
        Long studentId,
        String fullName,
        String universityEmail,
        boolean emailVerified,
        int weeklyCredits,
        String message
) {
}
