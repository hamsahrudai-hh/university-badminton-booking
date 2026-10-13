package edu.university.badminton.dto;

import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.TimeSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Public response representation of a dated time slot.
 */
public record TimeSlotResponseDto(
        Long id,
        Long courtId,
        Integer courtNumber,
        SessionType sessionType,
        LocalDate slotDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        SlotStatus status
) {
    public static TimeSlotResponseDto fromEntity(TimeSlot slot) {
        return new TimeSlotResponseDto(
                slot.getId(),
                slot.getCourt() != null ? slot.getCourt().getId() : null,
                slot.getCourt() != null ? slot.getCourt().getCourtNumber() : null,
                slot.getSessionConfig() != null ? slot.getSessionConfig().getSessionType() : null,
                slot.getSlotDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getStatus()
        );
    }
}
