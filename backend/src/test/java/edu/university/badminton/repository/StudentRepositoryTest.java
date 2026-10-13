package edu.university.badminton.repository;

import edu.university.badminton.model.Role;
import edu.university.badminton.model.Student;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("Should save student and populate defaults for credits, reliability, and active status")
    void shouldSaveStudentWithDefaultValues() {
        Student student = new Student(
                "Alex Johnson",
                "alex.johnson@university.edu",
                "U2026001",
                Role.STUDENT
        );

        Student saved = studentRepository.saveAndFlush(student);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFullName()).isEqualTo("Alex Johnson");
        assertThat(saved.getUniversityEmail()).isEqualTo("alex.johnson@university.edu");
        assertThat(saved.getAdmissionNumber()).isEqualTo("U2026001");
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getWeeklyCredits()).isEqualTo(6);
        assertThat(saved.getReliabilityRating()).isEqualTo(100.0);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find student by unique university email")
    void shouldFindByUniversityEmail() {
        Student student = new Student(
                "Sam Taylor",
                "sam.taylor@university.edu",
                "U2026002",
                Role.STUDENT
        );
        studentRepository.saveAndFlush(student);

        Optional<Student> found = studentRepository.findByUniversityEmail("sam.taylor@university.edu");

        assertThat(found).isPresent();
        assertThat(found.get().getAdmissionNumber()).isEqualTo("U2026002");
    }

    @Test
    @DisplayName("Should find student by unique admission number")
    void shouldFindByAdmissionNumber() {
        Student student = new Student(
                "Morgan Lee",
                "morgan.lee@university.edu",
                "U2026003",
                Role.STAFF
        );
        studentRepository.saveAndFlush(student);

        Optional<Student> found = studentRepository.findByAdmissionNumber("U2026003");

        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(Role.STAFF);
    }

    @Test
    @DisplayName("Should check existence by email and admission number")
    void shouldCheckExistenceByEmailAndAdmissionNumber() {
        Student student = new Student(
                "Jordan Patel",
                "jordan.patel@university.edu",
                "U2026004",
                Role.ADMIN
        );
        studentRepository.saveAndFlush(student);

        assertThat(studentRepository.existsByUniversityEmail("jordan.patel@university.edu")).isTrue();
        assertThat(studentRepository.existsByUniversityEmail("unknown@university.edu")).isFalse();
        assertThat(studentRepository.existsByAdmissionNumber("U2026004")).isTrue();
        assertThat(studentRepository.existsByAdmissionNumber("U9999999")).isFalse();
    }

    @Test
    @DisplayName("Should reject duplicate university email")
    void shouldRejectDuplicateUniversityEmail() {
        Student student1 = new Student(
                "Student One",
                "duplicate@university.edu",
                "U2026005",
                Role.STUDENT
        );
        studentRepository.saveAndFlush(student1);

        Student student2 = new Student(
                "Student Two",
                "duplicate@university.edu",
                "U2026006",
                Role.STUDENT
        );

        assertThatThrownBy(() -> studentRepository.saveAndFlush(student2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should reject duplicate admission number")
    void shouldRejectDuplicateAdmissionNumber() {
        Student student1 = new Student(
                "Student One",
                "email1@university.edu",
                "U_DUP_001",
                Role.STUDENT
        );
        studentRepository.saveAndFlush(student1);

        Student student2 = new Student(
                "Student Two",
                "email2@university.edu",
                "U_DUP_001",
                Role.STUDENT
        );

        assertThatThrownBy(() -> studentRepository.saveAndFlush(student2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
