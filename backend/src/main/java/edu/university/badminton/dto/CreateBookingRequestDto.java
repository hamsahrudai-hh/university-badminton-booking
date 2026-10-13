package edu.university.badminton.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for confirming a badminton court reservation for a group.
 */
public record CreateBookingRequestDto(
        Long bookingStudentId,
        Long timeSlotId,
        List<BookingPlayerAssignmentDto> players,
        String idempotencyKey
) {

    /**
     * Convenience factory for creating a request with a simple list of student IDs.
     * Spots are assigned sequentially (1, 2, 3, 4).
     */
    public static CreateBookingRequestDto of(Long bookingStudentId, Long timeSlotId, List<Long> studentIds, String idempotencyKey) {
        List<BookingPlayerAssignmentDto> playerDtos = new ArrayList<>();
        if (studentIds != null) {
            for (int i = 0; i < studentIds.size(); i++) {
                playerDtos.add(new BookingPlayerAssignmentDto(studentIds.get(i), i + 1));
            }
        }
        return new CreateBookingRequestDto(bookingStudentId, timeSlotId, playerDtos, idempotencyKey);
    }
}
