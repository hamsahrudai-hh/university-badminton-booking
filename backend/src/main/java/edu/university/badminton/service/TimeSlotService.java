package edu.university.badminton.service;

import edu.university.badminton.dto.SlotGenerationResponseDto;
import edu.university.badminton.dto.TimeSlotResponseDto;
import edu.university.badminton.model.Court;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SlotStatus;
import edu.university.badminton.model.TimeSlot;
import edu.university.badminton.repository.CourtRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.TimeSlotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service providing time slot queries and safe idempotent slot generation.
 */
@Service
@Transactional(readOnly = true)
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final CourtRepository courtRepository;
    private final SessionConfigRepository sessionConfigRepository;
    private final TimeSlotBatchWriter timeSlotBatchWriter;

    public TimeSlotService(TimeSlotRepository timeSlotRepository,
                           CourtRepository courtRepository,
                           SessionConfigRepository sessionConfigRepository,
                           TimeSlotBatchWriter timeSlotBatchWriter) {
        this.timeSlotRepository = timeSlotRepository;
        this.courtRepository = courtRepository;
        this.sessionConfigRepository = sessionConfigRepository;
        this.timeSlotBatchWriter = timeSlotBatchWriter;
    }

    public List<TimeSlotResponseDto> getSlotsByDate(LocalDate date) {
        return timeSlotRepository.findBySlotDateWithDetails(date)
                .stream()
                .map(TimeSlotResponseDto::fromEntity)
                .toList();
    }

    /**
     * Safely and idempotently generates time slots for a given date across all active courts
     * and active session configurations.
     * <p>
     * Persisting new slots is delegated to {@link TimeSlotBatchWriter} using an isolated
     * {@code REQUIRES_NEW} transaction so that concurrent duplicate key races do not mark
     * the caller's transaction rollback-only.
     *
     * @param date the target calendar date
     * @return summary of slots created and existing slots preserved
     */
    public SlotGenerationResponseDto generateSlotsForDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null.");
        }

        List<Court> activeCourts = courtRepository.findByActiveTrueOrderByCourtNumberAsc();
        if (activeCourts.isEmpty()) {
            throw new IllegalStateException("No active courts found. At least one active court is required to generate time slots.");
        }

        List<SessionConfig> activeSessions = sessionConfigRepository.findByActiveTrueOrderByStartTimeAsc();
        if (activeSessions.isEmpty()) {
            throw new IllegalStateException("No active session configurations found. Active session configurations are required to generate time slots.");
        }

        // Validate session configurations to ensure no zero-length or inverted time slots can be generated
        for (SessionConfig session : activeSessions) {
            if (session.getSlotDurationMinutes() == null || session.getSlotDurationMinutes() <= 0) {
                throw new IllegalStateException("Session configuration " + session.getSessionType()
                        + " has invalid slot duration: " + session.getSlotDurationMinutes() + " minutes.");
            }
            if (session.getStartTime() == null || session.getEndTime() == null
                    || !session.getStartTime().isBefore(session.getEndTime())) {
                throw new IllegalStateException("Session configuration " + session.getSessionType()
                        + " has invalid time window: start time (" + session.getStartTime()
                        + ") must be strictly before end time (" + session.getEndTime() + ").");
            }
        }

        // Fetch existing slots for this date to preserve them without alteration
        List<TimeSlot> existingSlots = timeSlotRepository.findBySlotDateWithDetails(date);
        Set<String> existingKeys = existingSlots.stream()
                .map(slot -> slot.getCourt().getId() + "#" + slot.getStartTime())
                .collect(Collectors.toSet());

        List<TimeSlot> newSlotsToCreate = new ArrayList<>();

        for (Court court : activeCourts) {
            for (SessionConfig session : activeSessions) {
                int durationMinutes = session.getSlotDurationMinutes();

                LocalTime currentStartTime = session.getStartTime();
                LocalTime sessionEndTime = session.getEndTime();

                while (currentStartTime.plusMinutes(durationMinutes).isBefore(sessionEndTime)
                        || currentStartTime.plusMinutes(durationMinutes).equals(sessionEndTime)) {
                    LocalTime slotEndTime = currentStartTime.plusMinutes(durationMinutes);

                    LocalDateTime startDateTime = LocalDateTime.of(date, currentStartTime);
                    LocalDateTime endDateTime = LocalDateTime.of(date, slotEndTime);

                    String slotKey = court.getId() + "#" + startDateTime;
                    if (!existingKeys.contains(slotKey)) {
                        TimeSlot slot = new TimeSlot(
                                court,
                                session,
                                date,
                                startDateTime,
                                endDateTime,
                                SlotStatus.AVAILABLE
                        );
                        newSlotsToCreate.add(slot);
                    }

                    currentStartTime = slotEndTime;
                }
            }
        }

        int createdCount = 0;
        if (!newSlotsToCreate.isEmpty()) {
            try {
                createdCount = timeSlotBatchWriter.writeSlots(newSlotsToCreate);
            } catch (DataIntegrityViolationException e) {
                // Duplicate key race condition during concurrent generation:
                // The isolated inner transaction rolled back cleanly, leaving this context unpolluted.
                createdCount = 0;
            }
        }

        if (createdCount == 0 && !newSlotsToCreate.isEmpty()) {
            // Re-fetch existing slots in case another concurrent transaction just inserted them
            existingSlots = timeSlotRepository.findBySlotDateWithDetails(date);
        }

        int existingCount = existingSlots.size();
        int totalCount = createdCount > 0 ? existingCount + createdCount : existingCount;

        String message;
        if (createdCount > 0) {
            message = String.format("Successfully generated %d time slots for %s across %d courts (%d pre-existing slots preserved).",
                    createdCount, date, activeCourts.size(), existingCount);
        } else {
            message = String.format("Slots already exist for %s. 0 new slots created, %d existing slots preserved.",
                    date, existingCount);
        }

        return new SlotGenerationResponseDto(
                date,
                totalCount,
                createdCount,
                existingCount,
                message
        );
    }
}
