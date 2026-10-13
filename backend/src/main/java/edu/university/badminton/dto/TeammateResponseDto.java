package edu.university.badminton.dto;

import edu.university.badminton.model.Student;

/**
 * Privacy-safe representation of an eligible teammate for group court booking.
 * Exposes only database ID, display name, and eligibility status.
 * Prevents public exposure of student admission numbers, university emails,
 * credit balances, reliability scores, or authentication data.
 */
public record TeammateResponseDto(
        Long id,
        String fullName,
        Boolean eligible
) {
    public static TeammateResponseDto fromEntity(Student student) {
        boolean isEligible = Boolean.TRUE.equals(student.getActive())
                && Boolean.TRUE.equals(student.getEmailVerified())
                && student.getWeeklyCredits() != null
                && student.getWeeklyCredits() >= 1;
        return new TeammateResponseDto(
                student.getId(),
                student.getFullName(),
                isEligible
        );
    }
}
