package edu.university.badminton.dto;

/**
 * Request payload for student account registration.
 */
public record RegisterStudentRequestDto(
        String fullName,
        String universityEmail,
        String admissionNumber,
        String password
) {
}
