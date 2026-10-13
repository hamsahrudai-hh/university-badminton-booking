package edu.university.badminton.dto;

import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeammateResponseDtoTest {

    @Test
    @DisplayName("TeammateResponseDto maps only id, fullName, and eligible from Student entity")
    void shouldMapOnlyPublicFields() {
        Student student = new Student("Alice Walker", "alice.walker@vitapstudent.ac.in", "23MIC9999", Role.STUDENT);
        student.setId(42L);
        student.setWeeklyCredits(5);
        student.setActive(true);
        student.setEmailVerified(true);
        student.setReliabilityRating(0.95);

        TeammateResponseDto dto = TeammateResponseDto.fromEntity(student);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.fullName()).isEqualTo("Alice Walker");
        assertThat(dto.eligible()).isTrue();
    }

    @Test
    @DisplayName("TeammateResponseDto marks ineligible when student has 0 weekly credits")
    void shouldMarkIneligibleWhenZeroCredits() {
        Student student = new Student("Bob Stone", "bob.stone@vitapstudent.ac.in", "23MIC8888", Role.STUDENT);
        student.setId(43L);
        student.setWeeklyCredits(0);
        student.setActive(true);
        student.setEmailVerified(true);

        TeammateResponseDto dto = TeammateResponseDto.fromEntity(student);

        assertThat(dto.eligible()).isFalse();
    }

    @Test
    @DisplayName("TeammateResponseDto marks ineligible when student is inactive or unverified")
    void shouldMarkIneligibleWhenInactiveOrUnverified() {
        Student inactiveStudent = new Student("Charlie", "charlie@vitapstudent.ac.in", "23MIC7777", Role.STUDENT);
        inactiveStudent.setId(44L);
        inactiveStudent.setWeeklyCredits(6);
        inactiveStudent.setActive(false);
        inactiveStudent.setEmailVerified(true);

        assertThat(TeammateResponseDto.fromEntity(inactiveStudent).eligible()).isFalse();

        Student unverifiedStudent = new Student("Dana", "dana@vitapstudent.ac.in", "23MIC6666", Role.STUDENT);
        unverifiedStudent.setId(45L);
        unverifiedStudent.setWeeklyCredits(6);
        unverifiedStudent.setActive(true);
        unverifiedStudent.setEmailVerified(false);

        assertThat(TeammateResponseDto.fromEntity(unverifiedStudent).eligible()).isFalse();
    }

    @Test
    @DisplayName("TeammateResponseDto record class does not contain private fields like email or admissionNumber")
    void shouldNotDeclarePrivateFields() {
        List<String> recordComponentNames = Arrays.stream(TeammateResponseDto.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(recordComponentNames).containsExactlyInAnyOrder("id", "fullName", "eligible");
        assertThat(recordComponentNames).doesNotContain(
                "universityEmail", "email", "admissionNumber", "weeklyCredits",
                "reliabilityRating", "role", "password", "passwordHash", "token", "verificationToken"
        );
    }
}
