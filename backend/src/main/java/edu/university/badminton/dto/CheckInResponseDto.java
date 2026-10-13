package edu.university.badminton.dto;

import edu.university.badminton.model.BookingPlayer;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Public response representation of a successful or idempotent player check-in.
 */
public record CheckInResponseDto(
        boolean success,
        String message,
        boolean alreadyCheckedIn,
        Long bookingId,
        Long bookingPlayerId,
        Long studentId,
        String studentName,
        String admissionNumber,
        Integer spotNumber,
        Long courtId,
        Integer courtNumber,
        LocalDateTime slotStartTime,
        LocalDateTime slotEndTime,
        Instant checkedInAt,
        String checkedInBy
) {
    public static CheckInResponseDto fromEntity(BookingPlayer player, boolean alreadyCheckedIn, String message) {
        return new CheckInResponseDto(
                true,
                message,
                alreadyCheckedIn,
                player.getBooking().getId(),
                player.getId(),
                player.getStudent().getId(),
                player.getStudent().getFullName(),
                player.getStudent().getAdmissionNumber(),
                player.getSpotNumber(),
                player.getBooking().getTimeSlot().getCourt().getId(),
                player.getBooking().getTimeSlot().getCourt().getCourtNumber(),
                player.getBooking().getTimeSlot().getStartTime(),
                player.getBooking().getTimeSlot().getEndTime(),
                player.getCheckedInAt(),
                player.getCheckedInBy()
        );
    }
}
