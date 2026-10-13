package edu.university.badminton.repository;

import edu.university.badminton.model.Court;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Court entities.
 */
@Repository
public interface CourtRepository extends JpaRepository<Court, Long> {

    Optional<Court> findByCourtNumber(Integer courtNumber);

    boolean existsByCourtNumber(Integer courtNumber);

    List<Court> findAllByOrderByCourtNumberAsc();

    List<Court> findByActiveTrueOrderByCourtNumberAsc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Court c WHERE c.id = :id")
    Optional<Court> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
}
