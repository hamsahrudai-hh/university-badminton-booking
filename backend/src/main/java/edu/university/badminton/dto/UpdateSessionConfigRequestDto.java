package edu.university.badminton.dto;

import java.time.LocalTime;

/**
 * Request payload for updating recurring session configuration rules.
 */
public record UpdateSessionConfigRequestDto(
        LocalTime startTime,
        LocalTime endTime,
        Integer slotDurationMinutes,
        LocalTime queueOpenTime,
        LocalTime bookingStartTime,
        Integer queueDaysInAdvance,
        Boolean active
) {
}
