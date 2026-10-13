package edu.university.badminton.repository;

import edu.university.badminton.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Booking entities.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByTimeSlotId(Long timeSlotId);

    boolean existsByTimeSlotId(Long timeSlotId);

    List<Booking> findByBookingUserIdOrderByCreatedAtDesc(Long bookingUserId);
}
