package edu.university.badminton.repository;

import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.SessionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Spring Data JPA repository for BookingQueue entities.
 */
@Repository
public interface BookingQueueRepository extends JpaRepository<BookingQueue, Long> {

    Optional<BookingQueue> findBySessionDateAndSessionType(LocalDate sessionDate, SessionType sessionType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bq FROM BookingQueue bq WHERE bq.id = :id")
    Optional<BookingQueue> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bq FROM BookingQueue bq WHERE bq.sessionDate = :sessionDate AND bq.sessionType = :sessionType")
    Optional<BookingQueue> findBySessionDateAndSessionTypeForUpdate(
            @Param("sessionDate") LocalDate sessionDate,
            @Param("sessionType") SessionType sessionType
    );
}
