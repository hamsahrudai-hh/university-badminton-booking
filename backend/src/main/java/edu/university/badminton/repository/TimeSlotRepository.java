package edu.university.badminton.repository;

import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for TimeSlot entities.
 */
@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findBySlotDate(LocalDate slotDate);

    List<TimeSlot> findByCourtIdAndSlotDate(Long courtId, LocalDate slotDate);

    Optional<TimeSlot> findByCourtIdAndStartTime(Long courtId, LocalDateTime startTime);

    boolean existsByCourtIdAndStartTime(Long courtId, LocalDateTime startTime);

    List<TimeSlot> findBySlotDateAndStatus(LocalDate slotDate, SlotStatus status);

    @Query("SELECT ts FROM TimeSlot ts JOIN FETCH ts.court c JOIN FETCH ts.sessionConfig sc WHERE ts.slotDate = :slotDate ORDER BY ts.startTime ASC, c.courtNumber ASC")
    List<TimeSlot> findBySlotDateWithDetails(@Param("slotDate") LocalDate slotDate);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ts FROM TimeSlot ts WHERE ts.id = :id")
    Optional<TimeSlot> findByIdWithLock(@Param("id") Long id);
}
