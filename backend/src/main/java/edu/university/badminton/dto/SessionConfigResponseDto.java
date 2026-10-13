package edu.university.badminton.dto;

import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;

import java.time.LocalTime;

/**
 * Public response representation of a recurring session configuration.
 */
public record SessionConfigResponseDto(
        Long id,
        SessionType sessionType,
        LocalTime startTime,
        LocalTime endTime,
        Integer slotDurationMinutes,
        LocalTime queueOpenTime,
        LocalTime bookingStartTime,
        Integer queueDaysInAdvance,
        Boolean active
) {
    public static SessionConfigResponseDto fromEntity(SessionConfig config) {
        return new SessionConfigResponseDto(
                config.getId(),
                config.getSessionType(),
                config.getStartTime(),
                config.getEndTime(),
                config.getSlotDurationMinutes(),
                config.getQueueOpenTime(),
                config.getBookingStartTime(),
                config.getQueueDaysInAdvance(),
                config.getActive()
        );
    }
}
