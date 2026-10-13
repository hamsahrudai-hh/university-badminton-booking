package edu.university.badminton.repository;

import edu.university.badminton.model.BookingPlayer;
import edu.university.badminton.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for BookingPlayer entities.
 */
@Repository
public interface BookingPlayerRepository extends JpaRepository<BookingPlayer, Long> {

    List<BookingPlayer> findByBookingId(Long bookingId);

    Optional<BookingPlayer> findByQrToken(String qrToken);

    @Query("SELECT bp FROM BookingPlayer bp " +
           "JOIN FETCH bp.booking b " +
           "JOIN FETCH b.timeSlot ts " +
           "JOIN FETCH ts.court c " +
           "JOIN FETCH bp.student s " +
           "WHERE bp.qrToken = :qrToken")
    Optional<BookingPlayer> findByQrTokenWithDetails(@Param("qrToken") String qrToken);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BookingPlayer bp " +
           "JOIN FETCH bp.booking b " +
           "JOIN FETCH b.timeSlot ts " +
           "JOIN FETCH ts.court c " +
           "JOIN FETCH bp.student s " +
           "WHERE bp.qrToken = :qrToken")
    Optional<BookingPlayer> findByQrTokenWithDetailsAndLock(@Param("qrToken") String qrToken);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BookingPlayer bp " +
           "JOIN FETCH bp.booking b " +
           "JOIN FETCH b.timeSlot ts " +
           "JOIN FETCH ts.court c " +
           "JOIN FETCH bp.student s " +
           "WHERE bp.id = :id")
    Optional<BookingPlayer> findByIdWithDetailsAndLock(@Param("id") Long id);

    @Query("SELECT bp FROM BookingPlayer bp " +
           "JOIN FETCH bp.booking b " +
           "JOIN FETCH b.timeSlot ts " +
           "JOIN FETCH ts.court c " +
           "JOIN FETCH bp.student s " +
           "WHERE bp.id = :id")
    Optional<BookingPlayer> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT bp.id FROM BookingPlayer bp " +
           "JOIN bp.booking b " +
           "JOIN b.timeSlot ts " +
           "WHERE bp.checkedIn = false " +
           "AND bp.noShow = false " +
           "AND b.status = :status " +
           "AND ts.startTime < :cutoffTime")
    List<Long> findPendingNoShowIds(
            @Param("status") BookingStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    @Query("SELECT bp.id FROM BookingPlayer bp " +
           "JOIN bp.booking b " +
           "JOIN b.timeSlot ts " +
           "WHERE bp.checkedIn = false " +
           "AND bp.noShow = false " +
           "AND b.status = :status " +
           "AND ts.id = :timeSlotId " +
           "AND ts.startTime < :cutoffTime")
    List<Long> findPendingNoShowIdsForSlot(
            @Param("status") BookingStatus status,
            @Param("timeSlotId") Long timeSlotId,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    @Query("SELECT bp FROM BookingPlayer bp " +
           "JOIN FETCH bp.booking b " +
           "JOIN FETCH b.timeSlot ts " +
           "JOIN FETCH ts.court c " +
           "JOIN FETCH bp.student s " +
           "WHERE bp.checkedIn = false " +
           "AND bp.noShow = false " +
           "AND b.status = :status " +
           "AND ts.startTime < :cutoffTime")
    List<BookingPlayer> findPendingNoShows(
            @Param("status") BookingStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    @Query("SELECT bp FROM BookingPlayer bp " +
           "JOIN FETCH bp.booking b " +
           "JOIN FETCH b.timeSlot ts " +
           "JOIN FETCH ts.court c " +
           "JOIN FETCH bp.student s " +
           "WHERE bp.checkedIn = false " +
           "AND bp.noShow = false " +
           "AND b.status = :status " +
           "AND ts.id = :timeSlotId " +
           "AND ts.startTime < :cutoffTime")
    List<BookingPlayer> findPendingNoShowsForSlot(
            @Param("status") BookingStatus status,
            @Param("timeSlotId") Long timeSlotId,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    @Query("SELECT COUNT(bp) > 0 FROM BookingPlayer bp " +
           "JOIN bp.booking b " +
           "JOIN b.timeSlot ts " +
           "WHERE bp.student.id = :studentId " +
           "AND b.status = :status " +
           "AND ts.startTime = :startTime")
    boolean existsByStudentIdAndSlotStartTimeAndBookingStatus(
            @Param("studentId") Long studentId,
            @Param("startTime") LocalDateTime startTime,
            @Param("status") BookingStatus status
    );
}
