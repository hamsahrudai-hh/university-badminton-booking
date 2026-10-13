package edu.university.badminton.service;

import edu.university.badminton.dto.SessionConfigResponseDto;
import edu.university.badminton.dto.UpdateSessionConfigRequestDto;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.repository.SessionConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service providing session configuration queries and administrative rule management.
 */
@Service
@Transactional(readOnly = true)
public class SessionConfigService {

    private final SessionConfigRepository sessionConfigRepository;

    public SessionConfigService(SessionConfigRepository sessionConfigRepository) {
        this.sessionConfigRepository = sessionConfigRepository;
    }

    /**
     * Retrieves all session configurations ordered by operating start time.
     */
    public List<SessionConfigResponseDto> getAllSessions() {
        return sessionConfigRepository.findAllByOrderByStartTimeAsc()
                .stream()
                .map(SessionConfigResponseDto::fromEntity)
                .toList();
    }

    /**
     * Retrieves a specific session configuration by its database ID.
     */
    public SessionConfigResponseDto getSessionById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Session ID cannot be null.");
        }
        SessionConfig config = sessionConfigRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Session configuration not found with ID: " + id));
        return SessionConfigResponseDto.fromEntity(config);
    }

    /**
     * Retrieves a specific session configuration by its session type.
     */
    public SessionConfigResponseDto getSessionByType(SessionType sessionType) {
        if (sessionType == null) {
            throw new IllegalArgumentException("Session type cannot be null.");
        }
        SessionConfig config = sessionConfigRepository.findBySessionType(sessionType)
                .orElseThrow(() -> new NoSuchElementException("Session configuration not found for session type: " + sessionType));
        return SessionConfigResponseDto.fromEntity(config);
    }

    /**
     * Safely updates recurring session rules for future slot generation and queue admissions.
     * Preserves existing historical slots, bookings, and player passes without alteration.
     */
    @Transactional
    public SessionConfigResponseDto updateSessionConfig(Long id, UpdateSessionConfigRequestDto request) {
        if (id == null) {
            throw new IllegalArgumentException("Session ID cannot be null.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Update request payload cannot be null.");
        }

        SessionConfig config = sessionConfigRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Session configuration not found with ID: " + id));

        // Merge incoming fields with existing database configuration
        LocalTime targetStartTime = request.startTime() != null ? request.startTime() : config.getStartTime();
        LocalTime targetEndTime = request.endTime() != null ? request.endTime() : config.getEndTime();
        Integer targetDuration = request.slotDurationMinutes() != null ? request.slotDurationMinutes() : config.getSlotDurationMinutes();
        LocalTime targetQueueOpenTime = request.queueOpenTime() != null ? request.queueOpenTime() : config.getQueueOpenTime();
        LocalTime targetBookingStartTime = request.bookingStartTime() != null ? request.bookingStartTime() : config.getBookingStartTime();
        Integer targetQueueAdvance = request.queueDaysInAdvance() != null ? request.queueDaysInAdvance() : config.getQueueDaysInAdvance();
        Boolean targetActive = request.active() != null ? request.active() : config.getActive();

        // 1. Validate session operating time window
        if (targetStartTime == null || targetEndTime == null) {
            throw new IllegalArgumentException("Session start time and end time cannot be null.");
        }
        if (!targetStartTime.isBefore(targetEndTime)) {
            throw new IllegalArgumentException(String.format(
                    "Session start time (%s) must be strictly before end time (%s).",
                    targetStartTime, targetEndTime
            ));
        }

        // 2. Validate slot duration
        if (targetDuration == null || targetDuration <= 0) {
            throw new IllegalArgumentException("Slot duration must be a positive integer greater than zero.");
        }
        long totalSessionMinutes = Duration.between(targetStartTime, targetEndTime).toMinutes();
        if (targetDuration > totalSessionMinutes) {
            throw new IllegalArgumentException(String.format(
                    "Slot duration (%d minutes) cannot exceed total session window (%d minutes).",
                    targetDuration, totalSessionMinutes
            ));
        }

        // 3. Validate queue opening and booking admission sequence
        if (targetQueueOpenTime == null || targetBookingStartTime == null) {
            throw new IllegalArgumentException("Queue opening time and booking admission start time cannot be null.");
        }
        if (!targetQueueOpenTime.isBefore(targetBookingStartTime)) {
            throw new IllegalArgumentException(String.format(
                    "Queue opening time (%s) must be strictly before booking admission start time (%s).",
                    targetQueueOpenTime, targetBookingStartTime
            ));
        }

        // 4. Validate advance queue notice
        if (targetQueueAdvance == null || targetQueueAdvance < 0) {
            throw new IllegalArgumentException("Queue advance days cannot be negative.");
        }

        // 5. Prevent overlapping active sessions
        if (Boolean.TRUE.equals(targetActive)) {
            List<SessionConfig> allSessions = sessionConfigRepository.findAll();
            for (SessionConfig other : allSessions) {
                if (!other.getId().equals(id) && Boolean.TRUE.equals(other.getActive())) {
                    if (isOverlapping(targetStartTime, targetEndTime, other.getStartTime(), other.getEndTime())) {
                        throw new IllegalArgumentException(String.format(
                                "Session time window [%s - %s] overlaps with existing active session %s [%s - %s]. Overlapping sessions are not allowed.",
                                targetStartTime, targetEndTime, other.getSessionType(), other.getStartTime(), other.getEndTime()
                        ));
                    }
                }
            }
        }

        // Apply validated changes
        config.setStartTime(targetStartTime);
        config.setEndTime(targetEndTime);
        config.setSlotDurationMinutes(targetDuration);
        config.setQueueOpenTime(targetQueueOpenTime);
        config.setBookingStartTime(targetBookingStartTime);
        config.setQueueDaysInAdvance(targetQueueAdvance);
        config.setActive(targetActive);

        SessionConfig saved = sessionConfigRepository.save(config);
        return SessionConfigResponseDto.fromEntity(saved);
    }

    private boolean isOverlapping(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }
}
