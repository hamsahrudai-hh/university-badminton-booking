package edu.university.badminton.dto;

import edu.university.badminton.model.Booking;
import edu.university.badminton.model.BookingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public response representation of a confirmed badminton reservation.
 */
public record BookingResponseDto(
        Long bookingId,
        Long timeSlotId,
        Integer courtNumber,
        LocalDate slotDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long bookingStudentId,
        String bookingStudentName,
        BookingStatus status,
        int creditsDeductedPerPlayer,
        List<BookingPlayerResponseDto> players,
        Instant createdAt
) {
    public static BookingResponseDto fromEntity(Booking booking) {
        List<BookingPlayerResponseDto> playerDtos = booking.getPlayers()
                .stream()
                .map(BookingPlayerResponseDto::fromEntity)
                .toList();

        return new BookingResponseDto(
                booking.getId(),
                booking.getTimeSlot().getId(),
                booking.getTimeSlot().getCourt() != null ? booking.getTimeSlot().getCourt().getCourtNumber() : null,
                booking.getTimeSlot().getSlotDate(),
                booking.getTimeSlot().getStartTime(),
                booking.getTimeSlot().getEndTime(),
                booking.getBookingUser().getId(),
                booking.getBookingUser().getFullName(),
                booking.getStatus(),
                1,
                playerDtos,
                booking.getCreatedAt()
        );
    }
}
