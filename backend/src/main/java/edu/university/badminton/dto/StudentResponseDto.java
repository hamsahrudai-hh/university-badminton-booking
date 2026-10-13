package edu.university.badminton.dto;

import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;

/**
 * Public response representation of a student profile.
 */
public record StudentResponseDto(
        Long id,
        String fullName,
        String universityEmail,
        String admissionNumber,
        Role role,
        Integer weeklyCredits,
        Double reliabilityRating,
        Boolean active,
        Boolean emailVerified
) {
    public StudentResponseDto(Long id, String fullName, String universityEmail, String admissionNumber, Role role, Integer weeklyCredits, Double reliabilityRating, Boolean active) {
        this(id, fullName, universityEmail, admissionNumber, role, weeklyCredits, reliabilityRating, active, true);
    }

    public static StudentResponseDto fromEntity(Student student) {
        return new StudentResponseDto(
                student.getId(),
                student.getFullName(),
                student.getUniversityEmail(),
                student.getAdmissionNumber(),
                student.getRole(),
                student.getWeeklyCredits(),
                student.getReliabilityRating(),
                student.getActive(),
                student.getEmailVerified()
        );
    }
}
