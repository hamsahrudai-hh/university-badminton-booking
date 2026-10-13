package edu.university.badminton.dto;

/**
 * Public response returned after a student initiates registration.
 * <p>
 * Does not expose password hashes, tokens, or sensitive account internals.
 */
public record RegistrationResponseDto(
        Long studentId,
        String fullName,
        String universityEmail,
        String admissionNumber,
        boolean emailVerified,
        String message
) {
}
