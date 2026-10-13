package edu.university.badminton.service;

import edu.university.badminton.dto.AdmitNextResponseDto;
import edu.university.badminton.dto.JoinQueueRequestDto;
import edu.university.badminton.dto.QueueEntryResponseDto;
import edu.university.badminton.dto.QueueStatusResponseDto;
import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.QueueEntryStatus;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.BookingQueueRepository;
import edu.university.badminton.repository.QueueEntryRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service managing the student booking queue, FIFO admissions, and 60-second booking opportunities.
 */
@Service
@Transactional(readOnly = true)
public class BookingQueueService {

    private final BookingQueueRepository bookingQueueRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final StudentRepository studentRepository;
    private final SessionConfigRepository sessionConfigRepository;
    private final BookingQueueCreator bookingQueueCreator;
    private final Clock clock;

    public BookingQueueService(BookingQueueRepository bookingQueueRepository,
                               QueueEntryRepository queueEntryRepository,
                               StudentRepository studentRepository,
                               SessionConfigRepository sessionConfigRepository,
                               BookingQueueCreator bookingQueueCreator,
                               Clock clock) {
        this.bookingQueueRepository = bookingQueueRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.studentRepository = studentRepository;
        this.sessionConfigRepository = sessionConfigRepository;
        this.bookingQueueCreator = bookingQueueCreator;
        this.clock = clock;
    }

    /**
     * Enters a student into the booking queue for a specific session date and session type.
     *
     * @param request the join queue request
     * @return response with the student's queue entry details and 1-based position in line
     */
    @Transactional
    public QueueEntryResponseDto joinQueue(JoinQueueRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Join queue request cannot be null.");
        }
        if (request.studentId() == null) {
            throw new IllegalArgumentException("Student ID is required.");
        }
        if (request.date() == null) {
            throw new IllegalArgumentException("Session date is required.");
        }
        if (request.sessionType() == null) {
            throw new IllegalArgumentException("Session type is required.");
        }

        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new NoSuchElementException("Student with ID " + request.studentId() + " not found."));

        if (!Boolean.TRUE.equals(student.getActive())) {
            throw new IllegalStateException("Student account is inactive. Inactive students cannot join the booking queue.");
        }
        if (!Boolean.TRUE.equals(student.getEmailVerified())) {
            throw new IllegalStateException("Student account has not verified university email. Unverified students cannot join the booking queue.");
        }

        SessionConfig sessionConfig = sessionConfigRepository.findBySessionType(request.sessionType())
                .orElseThrow(() -> new NoSuchElementException("Session configuration for " + request.sessionType() + " not found."));

        if (!Boolean.TRUE.equals(sessionConfig.getActive())) {
            throw new IllegalStateException("Session " + request.sessionType() + " is currently inactive.");
        }

        // Validate queue opening window using session configuration
        int daysInAdvance = sessionConfig.getQueueDaysInAdvance() != null ? sessionConfig.getQueueDaysInAdvance() : 1;
        LocalTime queueOpenTime = sessionConfig.getQueueOpenTime();
        LocalDateTime queueOpenDateTime = LocalDateTime.of(request.date().minusDays(daysInAdvance), queueOpenTime);

        LocalDateTime currentDateTime = LocalDateTime.now(clock);
        if (currentDateTime.isBefore(queueOpenDateTime)) {
            throw new IllegalStateException(String.format(
                    "Queue for %s session on %s has not opened yet. Queue opens on %s.",
                    request.sessionType(), request.date(), queueOpenDateTime
            ));
        }

        LocalDateTime sessionEndDateTime = LocalDateTime.of(request.date(), sessionConfig.getEndTime());
        if (currentDateTime.isAfter(sessionEndDateTime)) {
            throw new IllegalStateException("Cannot join queue for a session that has already ended on " + request.date() + ".");
        }

        // Find or create the BookingQueue for this date and session
        BookingQueue queue = getOrCreateQueue(sessionConfig, request.sessionType(), request.date());

        // Check if student already in queue
        if (queueEntryRepository.existsByQueueIdAndStudentId(queue.getId(), student.getId())) {
            throw new IllegalStateException("Student " + student.getFullName() + " is already in this booking queue.");
        }

        Instant now = clock.instant();
        QueueEntry entry = new QueueEntry(queue, student, now);

        try {
            entry = queueEntryRepository.save(entry);
            queueEntryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Student " + student.getFullName() + " is already in this booking queue.", e);
        }

        long position = calculateStudentPosition(queue.getId(), student, entry.getJoinedAt(), entry.getId());
        int tier = (student.getWeeklyCredits() != null && student.getWeeklyCredits() > 0) ? 0 : 1;
        String priorityNote = tier == 0 ? "" : " (Reduced priority: 0 weekly credits)";
        String message = String.format("Successfully joined the %s queue for %s at position %d%s.",
                request.sessionType(), request.date(), position, priorityNote);

        return QueueEntryResponseDto.fromEntity(entry, position, now, message);
    }

    /**
     * Atomically admits the next eligible student from the FIFO line to hold the 60-second booking opportunity.
     * Orders waiting students by credit priority (weekly credits > 0 first) followed by joined timestamp (FIFO).
     * Uses pessimistic locking on the queue to serialize concurrent admissions.
     *
     * @param date target session date
     * @param sessionType target session type
     * @return admission result DTO
     */
    @Transactional
    public AdmitNextResponseDto admitNextStudent(LocalDate date, SessionType sessionType) {
        if (date == null || sessionType == null) {
            throw new IllegalArgumentException("Session date and session type are required.");
        }

        Optional<BookingQueue> queueOpt = bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(date, sessionType);
        if (queueOpt.isEmpty()) {
            return new AdmitNextResponseDto(
                    null, date, sessionType, false, null, null, null, null, null, 0, 0,
                    "No booking queue exists for " + sessionType + " on " + date + "."
            );
        }

        BookingQueue queue = queueOpt.get();
        Instant now = clock.instant();
        LocalDateTime currentDateTime = LocalDateTime.now(clock);

        // Validate booking start time from session configuration
        SessionConfig sessionConfig = queue.getSessionConfig();
        int daysInAdvance = sessionConfig.getQueueDaysInAdvance() != null ? sessionConfig.getQueueDaysInAdvance() : 1;
        LocalTime bookingStartTime = sessionConfig.getBookingStartTime();
        LocalDateTime bookingStartDateTime = LocalDateTime.of(date.minusDays(daysInAdvance), bookingStartTime);

        if (currentDateTime.isBefore(bookingStartDateTime)) {
            long waiting = queueEntryRepository.countByQueueIdAndStatus(queue.getId(), QueueEntryStatus.WAITING);
            return new AdmitNextResponseDto(
                    queue.getId(), date, sessionType, false, null, null, null, null, null, 0, waiting,
                    String.format("Booking has not started yet for %s session on %s. Booking starts on %s.",
                            sessionType, date, bookingStartDateTime)
            );
        }

        LocalDateTime sessionEndDateTime = LocalDateTime.of(date, sessionConfig.getEndTime());
        if (currentDateTime.isAfter(sessionEndDateTime)) {
            return new AdmitNextResponseDto(
                    queue.getId(), date, sessionType, false, null, null, null, null, null, 0, 0,
                    "Session on " + date + " has already ended."
            );
        }

        // Check if an opportunity is currently active
        Optional<QueueEntry> activeEntryOpt = queueEntryRepository.findByQueueIdAndStatus(queue.getId(), QueueEntryStatus.ADMITTED);
        if (activeEntryOpt.isPresent()) {
            QueueEntry activeEntry = activeEntryOpt.get();
            if (activeEntry.isOpportunityActive(now)) {
                long remaining = activeEntry.getExpiresAt().getEpochSecond() - now.getEpochSecond();
                long waiting = queueEntryRepository.countByQueueIdAndStatus(queue.getId(), QueueEntryStatus.WAITING);
                return new AdmitNextResponseDto(
                        queue.getId(), date, sessionType, false,
                        null, null, null, // Privacy: do not expose other student's id/name
                        activeEntry.getAdmittedAt(), activeEntry.getExpiresAt(), Math.max(0, remaining), waiting,
                        "Booking opportunity is currently active (" + remaining + " seconds remaining)."
                );
            } else {
                // Current opportunity expired
                activeEntry.expire();
                queueEntryRepository.save(activeEntry);
                queueEntryRepository.flush();
            }
        }

        // Admit next waiting student in credit-based priority and FIFO order
        Optional<QueueEntry> nextWaitingOpt = queueEntryRepository.findWaitingEntriesOrderedByPriority(
                queue.getId(), QueueEntryStatus.WAITING
        ).stream().findFirst();

        if (nextWaitingOpt.isEmpty()) {
            return new AdmitNextResponseDto(
                    queue.getId(), date, sessionType, false, null, null, null, null, null, 0, 0,
                    "No students are currently waiting in the queue."
            );
        }

        QueueEntry nextStudent = nextWaitingOpt.get();
        nextStudent.admit(now);
        queueEntryRepository.save(nextStudent);
        queueEntryRepository.flush();

        long remainingWaiting = queueEntryRepository.countByQueueIdAndStatus(queue.getId(), QueueEntryStatus.WAITING);

        return new AdmitNextResponseDto(
                queue.getId(), date, sessionType, true,
                nextStudent.getId(), nextStudent.getStudent().getId(), nextStudent.getStudent().getFullName(),
                nextStudent.getAdmittedAt(), nextStudent.getExpiresAt(), QueueEntry.OPPORTUNITY_DURATION_SECONDS, remainingWaiting,
                "Student " + nextStudent.getStudent().getFullName() + " admitted to 60-second booking opportunity."
        );
    }

    /**
     * Inspects the current state of the queue, dynamically expiring any lapsed booking opportunities
     * and calculating authoritative timing windows using the server clock.
     * <p>
     * <strong>Privacy Protection:</strong> Does not expose active student names or IDs to callers.
     *
     * @param date target session date
     * @param sessionType target session type
     * @return snapshot of queue status and active opportunity
     */
    @Transactional
    public QueueStatusResponseDto getQueueStatus(LocalDate date, SessionType sessionType) {
        if (date == null || sessionType == null) {
            throw new IllegalArgumentException("Session date and session type are required.");
        }

        SessionConfig sessionConfig = sessionConfigRepository.findBySessionType(sessionType)
                .orElse(null);

        LocalDateTime queueOpenDateTime = null;
        LocalDateTime bookingStartDateTime = null;
        LocalDateTime sessionEndDateTime = null;
        LocalDateTime currentDateTime = LocalDateTime.now(clock);
        Instant now = clock.instant();

        boolean isQueueOpen = false;
        boolean isBookingOpen = false;

        if (sessionConfig != null) {
            int daysInAdvance = sessionConfig.getQueueDaysInAdvance() != null ? sessionConfig.getQueueDaysInAdvance() : 1;
            queueOpenTimeCalculation: {
                queueOpenDateTime = LocalDateTime.of(date.minusDays(daysInAdvance), sessionConfig.getQueueOpenTime());
                bookingStartDateTime = LocalDateTime.of(date.minusDays(daysInAdvance), sessionConfig.getBookingStartTime());
                sessionEndDateTime = LocalDateTime.of(date, sessionConfig.getEndTime());
            }

            isQueueOpen = !currentDateTime.isBefore(queueOpenDateTime) && !currentDateTime.isAfter(sessionEndDateTime);
            isBookingOpen = !currentDateTime.isBefore(bookingStartDateTime) && !currentDateTime.isAfter(sessionEndDateTime);
        }

        Optional<BookingQueue> queueOpt = bookingQueueRepository.findBySessionDateAndSessionType(date, sessionType);
        if (queueOpt.isEmpty()) {
            String queueMsg;
            if (!isQueueOpen) {
                queueMsg = String.format("Queue for %s session on %s is currently closed. Queue opens on %s.",
                        sessionType, date, queueOpenDateTime);
            } else if (!isBookingOpen) {
                queueMsg = String.format("Queue for %s session on %s is open! Booking admissions begin on %s.",
                        sessionType, date, bookingStartDateTime);
            } else {
                queueMsg = String.format("Booking queue is open for %s session on %s (0 students waiting).",
                        sessionType, date);
            }

            return new QueueStatusResponseDto(
                    null, date, sessionType, isQueueOpen ? "OPEN" : "CLOSED", 0, 0, false, null, null, null,
                    queueOpenDateTime, bookingStartDateTime, sessionEndDateTime, now, isQueueOpen, isBookingOpen,
                    queueMsg
            );
        }

        BookingQueue queue = queueOpt.get();

        Optional<QueueEntry> activeEntryOpt = queueEntryRepository.findByQueueIdAndStatus(queue.getId(), QueueEntryStatus.ADMITTED);
        QueueEntry activeEntry = null;
        if (activeEntryOpt.isPresent()) {
            QueueEntry candidate = activeEntryOpt.get();
            if (candidate.isOpportunityActive(now)) {
                activeEntry = candidate;
            } else {
                candidate.expire();
                queueEntryRepository.save(candidate);
                queueEntryRepository.flush();
            }
        }

        // If booking admissions have started and no opportunity is active, automatically advance the queue
        if (activeEntry == null && isBookingOpen) {
            try {
                AdmitNextResponseDto admitRes = admitNextStudent(date, sessionType);
                if (admitRes.studentAdmitted()) {
                    activeEntry = queueEntryRepository.findByQueueIdAndStatus(queue.getId(), QueueEntryStatus.ADMITTED)
                            .filter(candidate -> candidate.isOpportunityActive(now))
                            .orElse(null);
                }
            } catch (Exception ignored) {
                // Ignore concurrent admission races; queue is safely protected by database lock
            }
        }

        long totalEntries = queueEntryRepository.countByQueueId(queue.getId());
        long waitingCount = queueEntryRepository.countByQueueIdAndStatus(queue.getId(), QueueEntryStatus.WAITING);

        boolean hasActive = activeEntry != null;
        Long remaining = hasActive ? Math.max(0, activeEntry.getExpiresAt().getEpochSecond() - now.getEpochSecond()) : null;

        String message;
        if (hasActive) {
            message = "Active booking opportunity in progress (" + remaining + " seconds remaining).";
        } else if (!isQueueOpen) {
            message = "Queue is closed. Opens on " + queueOpenDateTime + ".";
        } else if (!isBookingOpen) {
            message = "Queue is open. Booking admissions begin on " + bookingStartDateTime + ".";
        } else {
            message = "Queue is active (" + waitingCount + " waiting).";
        }

        return new QueueStatusResponseDto(
                queue.getId(),
                date,
                sessionType,
                queue.getStatus(),
                totalEntries,
                waitingCount,
                hasActive,
                hasActive ? activeEntry.getAdmittedAt() : null,
                hasActive ? activeEntry.getExpiresAt() : null,
                remaining,
                queueOpenDateTime,
                bookingStartDateTime,
                sessionEndDateTime,
                now,
                isQueueOpen,
                isBookingOpen,
                message
        );
    }

    /**
     * Queries the specific position and opportunity status for a student in a queue.
     * Evaluates credit-based priority and dynamically admits waiting student if booking has started.
     *
     * @param studentId ID of the student
     * @param date target session date
     * @param sessionType target session type
     * @return details of the student's entry
     */
    @Transactional
    public QueueEntryResponseDto getStudentPosition(Long studentId, LocalDate date, SessionType sessionType) {
        if (studentId == null || date == null || sessionType == null) {
            throw new IllegalArgumentException("Student ID, date, and session type are required.");
        }

        BookingQueue queue = bookingQueueRepository.findBySessionDateAndSessionType(date, sessionType)
                .orElseThrow(() -> new NoSuchElementException("No queue found for " + sessionType + " on " + date + "."));

        QueueEntry entry = queueEntryRepository.findByQueueIdAndStudentId(queue.getId(), studentId)
                .orElseThrow(() -> new NoSuchElementException("Student is not registered in this queue."));

        Instant now = clock.instant();
        if (entry.getStatus() == QueueEntryStatus.ADMITTED && !entry.isOpportunityActive(now)) {
            entry.expire();
            queueEntryRepository.save(entry);
            queueEntryRepository.flush();
        }

        // If student is currently WAITING, check if queue should automatically advance and admit them
        if (entry.getStatus() == QueueEntryStatus.WAITING) {
            SessionConfig sessionConfig = queue.getSessionConfig();
            int daysInAdvance = sessionConfig.getQueueDaysInAdvance() != null ? sessionConfig.getQueueDaysInAdvance() : 1;
            LocalDateTime bookingStartDateTime = LocalDateTime.of(date.minusDays(daysInAdvance), sessionConfig.getBookingStartTime());
            LocalDateTime sessionEndDateTime = LocalDateTime.of(date, sessionConfig.getEndTime());
            LocalDateTime currentDateTime = LocalDateTime.now(clock);

            if (!currentDateTime.isBefore(bookingStartDateTime) && !currentDateTime.isAfter(sessionEndDateTime)) {
                boolean hasActive = queueEntryRepository.findByQueueIdAndStatus(queue.getId(), QueueEntryStatus.ADMITTED)
                        .filter(candidate -> candidate.isOpportunityActive(now))
                        .isPresent();
                if (!hasActive) {
                    try {
                        admitNextStudent(date, sessionType);
                        entry = queueEntryRepository.findById(entry.getId()).orElse(entry);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        Long position = null;
        String message;
        if (entry.getStatus() == QueueEntryStatus.WAITING) {
            position = calculateStudentPosition(queue.getId(), entry.getStudent(), entry.getJoinedAt(), entry.getId());
            int tier = (entry.getStudent().getWeeklyCredits() != null && entry.getStudent().getWeeklyCredits() > 0) ? 0 : 1;
            String priorityNote = tier == 0 ? "" : " (Reduced priority: 0 weekly credits)";
            message = "Waiting in queue at position " + position + "." + priorityNote;
        } else if (entry.getStatus() == QueueEntryStatus.ADMITTED) {
            long remaining = Math.max(0, entry.getExpiresAt().getEpochSecond() - now.getEpochSecond());
            message = "Currently admitted! You have " + remaining + " seconds remaining to complete your booking.";
        } else if (entry.getStatus() == QueueEntryStatus.EXPIRED) {
            message = "Your 60-second booking opportunity expired.";
        } else {
            message = "Queue entry status: " + entry.getStatus();
        }

        return QueueEntryResponseDto.fromEntity(entry, position, now, message);
    }

    private long calculateStudentPosition(Long queueId, Student student, Instant joinedAt, Long entryId) {
        int tier = (student != null && student.getWeeklyCredits() != null && student.getWeeklyCredits() > 0) ? 0 : 1;
        return queueEntryRepository.calculateWaitingPosition(queueId, tier, joinedAt, entryId);
    }

    private BookingQueue getOrCreateQueue(SessionConfig sessionConfig, SessionType sessionType, LocalDate date) {
        Optional<BookingQueue> existing = bookingQueueRepository.findBySessionDateAndSessionType(date, sessionType);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return bookingQueueCreator.createQueue(sessionConfig, sessionType, date);
        } catch (DataIntegrityViolationException e) {
            // Concurrent creation race: isolated REQUIRES_NEW transaction rolled back cleanly
            return bookingQueueRepository.findBySessionDateAndSessionType(date, sessionType)
                    .orElseThrow(() -> new IllegalStateException("Failed to retrieve concurrent queue.", e));
        }
    }
}
