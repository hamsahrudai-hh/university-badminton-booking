package edu.university.badminton.repository;

import edu.university.badminton.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for Student entities.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUniversityEmail(String universityEmail);

    Optional<Student> findByAdmissionNumber(String admissionNumber);

    boolean existsByUniversityEmail(String universityEmail);

    boolean existsByAdmissionNumber(String admissionNumber);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Student s WHERE s.id = :id")
    Optional<Student> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE Student s SET s.weeklyCredits = :credits WHERE s.role = edu.university.badminton.model.Role.STUDENT AND s.active = true")
    int resetEligibleStudentsWeeklyCredits(@org.springframework.data.repository.query.Param("credits") int credits);
}
