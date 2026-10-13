package edu.university.badminton.service;

import edu.university.badminton.dto.AdmitNextResponseDto;
import edu.university.badminton.dto.JoinQueueRequestDto;
import edu.university.badminton.dto.QueueEntryResponseDto;
import edu.university.badminton.dto.QueueStatusResponseDto;
import edu.university.badminton.model.BookingQueue;
import edu.university.badminton.model.QueueEntry;
import edu.university.badminton.model.QueueEntryStatus;
import edu.university.badminton.model.Role;
import edu.university.badminton.model.SessionConfig;
import edu.university.badminton.model.SessionType;
import edu.university.badminton.model.Student;
import edu.university.badminton.repository.BookingQueueRepository;
import edu.university.badminton.repository.QueueEntryRepository;
import edu.university.badminton.repository.SessionConfigRepository;
import edu.university.badminton.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingQueueServiceTest {

    @Mock
    private BookingQueueRepository bookingQueueRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SessionConfigRepository sessionConfigRepository;

    @Mock
    private BookingQueueCreator bookingQueueCreator;

    private ZoneId zoneId;
    private LocalDate targetDate;
    private SessionConfig morningConfig;
    private Student activeStudent1;
    private Student activeStudent2;
    private Student zeroCreditStudent;
    private Student inactiveStudent;

    @BeforeEach
    void setUp() {
        zoneId = ZoneId.of("UTC");
        targetDate = LocalDate.of(2026, 10, 15);

        // Morning session: starts 06:00, ends 08:30, queue opens 21:00 on day prior, booking starts 21:05
        morningConfig = new SessionConfig(
                SessionType.MORNING,
                LocalTime.of(6, 0),
                LocalTime.of(8, 30),
                30,
                LocalTime.of(21, 0),
                LocalTime.of(21, 5),
                1
        );
        morningConfig.setId(10L);

        activeStudent1 = new Student("Alice Student", "alice@university.edu", "S10001", Role.STUDENT);
        activeStudent1.setId(1L);

        activeStudent2 = new Student("Bob Student", "bob@university.edu", "S10002", Role.STUDENT);
        activeStudent2.setId(2L);

        zeroCreditStudent = new Student("Zero Credit", "zero@university.edu", "S10003", Role.STUDENT);
        zeroCreditStudent.setId(3L);
        zeroCreditStudent.setWeeklyCredits(0);

        inactiveStudent = new Student("Inactive Student", "inactive@university.edu", "S10004", Role.STUDENT);
        inactiveStudent.setId(4L);
        inactiveStudent.setActive(false);
    }

    private Clock createFixedClock(LocalDateTime ldt) {
        Instant instant = ldt.atZone(zoneId).toInstant();
        return Clock.fixed(instant, zoneId);
    }

    private BookingQueueService createService(Clock clock) {
        return new BookingQueueService(
                bookingQueueRepository, queueEntryRepository, studentRepository,
                sessionConfigRepository, bookingQueueCreator, clock
        );
    }

    @Test
    @DisplayName("Should reject joining queue before queue opening time")
    void shouldRejectJoiningBeforeQueueOpenTime() {
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 20, 59, 59));
        BookingQueueService service = createService(clock);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(activeStudent1));
        when(sessionConfigRepository.findBySessionType(SessionType.MORNING)).thenReturn(Optional.of(morningConfig));

        JoinQueueRequestDto request = new JoinQueueRequestDto(1L, targetDate, SessionType.MORNING);

        assertThatThrownBy(() -> service.joinQueue(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not opened yet");
    }

    @Test
    @DisplayName("Should permit joining queue during the interval between queue open time (21:00) and booking start time (21:05)")
    void shouldAllowJoiningDuringIntervalBetweenQueueOpenAndBookingStart() {
        // At 21:02:00: queue is open, but booking has not started
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 2, 0));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(activeStudent1));
        when(sessionConfigRepository.findBySessionType(SessionType.MORNING)).thenReturn(Optional.of(morningConfig));
        when(bookingQueueRepository.findBySessionDateAndSessionType(targetDate, SessionType.MORNING)).thenReturn(Optional.of(queue));
        when(queueEntryRepository.existsByQueueIdAndStudentId(100L, 1L)).thenReturn(false);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(inv -> {
            QueueEntry qe = inv.getArgument(0);
            qe.setId(501L);
            return qe;
        });
        when(queueEntryRepository.calculateWaitingPosition(eq(100L), eq(0), any(Instant.class), eq(501L))).thenReturn(1L);

        JoinQueueRequestDto request = new JoinQueueRequestDto(1L, targetDate, SessionType.MORNING);
        QueueEntryResponseDto response = service.joinQueue(request);

        assertThat(response.queueEntryId()).isEqualTo(501L);
        assertThat(response.studentId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(response.positionInLine()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should reject admission during the interval between queue open time (21:00) and booking start time (21:05)")
    void shouldRejectAdmissionDuringIntervalBetweenQueueOpenAndBookingStart() {
        // At 21:02:00: booking has not started yet
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 2, 0));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        when(bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(targetDate, SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.countByQueueIdAndStatus(100L, QueueEntryStatus.WAITING)).thenReturn(3L);

        AdmitNextResponseDto response = service.admitNextStudent(targetDate, SessionType.MORNING);

        assertThat(response.studentAdmitted()).isFalse();
        assertThat(response.waitingRemaining()).isEqualTo(3L);
        assertThat(response.message()).contains("Booking has not started yet");
        assertThat(response.message()).contains("2026-10-14T21:05");
    }

    @Test
    @DisplayName("Should allow admission at the exact booking start boundary (21:05:00)")
    void shouldAllowAdmissionAtExactBookingStartBoundary() {
        // Exactly at booking start boundary: 2026-10-14 21:05:00
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 5, 0));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        QueueEntry waitingStudent = new QueueEntry(queue, activeStudent1, clock.instant());
        waitingStudent.setId(501L);

        when(bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(targetDate, SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStatus(100L, QueueEntryStatus.ADMITTED))
                .thenReturn(Optional.empty());
        when(queueEntryRepository.findWaitingEntriesOrderedByPriority(100L, QueueEntryStatus.WAITING))
                .thenReturn(List.of(waitingStudent));
        when(queueEntryRepository.countByQueueIdAndStatus(100L, QueueEntryStatus.WAITING)).thenReturn(0L);

        AdmitNextResponseDto response = service.admitNextStudent(targetDate, SessionType.MORNING);

        assertThat(response.studentAdmitted()).isTrue();
        assertThat(response.studentId()).isEqualTo(1L);
        assertThat(response.studentName()).isEqualTo("Alice Student");
        assertThat(response.remainingSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Should ensure queue status does not expose active student name or ID to public callers")
    void shouldEnsureQueueStatusProtectsStudentPrivacy() {
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 14, 21, 5, 0);
        Clock clock = createFixedClock(t0.plusSeconds(20));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        QueueEntry active = new QueueEntry(queue, activeStudent1, t0.atZone(zoneId).toInstant());
        active.setId(501L);
        active.admit(t0.atZone(zoneId).toInstant());

        when(bookingQueueRepository.findBySessionDateAndSessionType(targetDate, SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStatus(100L, QueueEntryStatus.ADMITTED))
                .thenReturn(Optional.of(active));
        when(queueEntryRepository.countByQueueId(100L)).thenReturn(5L);
        when(queueEntryRepository.countByQueueIdAndStatus(100L, QueueEntryStatus.WAITING)).thenReturn(4L);

        QueueStatusResponseDto status = service.getQueueStatus(targetDate, SessionType.MORNING);

        assertThat(status.hasActiveOpportunity()).isTrue();
        assertThat(status.remainingSeconds()).isEqualTo(40L);
        assertThat(status.totalEntries()).isEqualTo(5L);
        assertThat(status.waitingCount()).isEqualTo(4L);
        assertThat(status.message()).doesNotContain("Alice Student");
        assertThat(status.message()).doesNotContain("1");
    }

    @Test
    @DisplayName("Should recover cleanly when concurrent first-time join encounters unique constraint race on queue")
    void shouldRecoverCleanlyOnConcurrentQueueCreationRace() {
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 2, 0));
        BookingQueueService service = createService(clock);

        BookingQueue createdByWinner = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        createdByWinner.setId(100L);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(activeStudent1));
        when(sessionConfigRepository.findBySessionType(SessionType.MORNING)).thenReturn(Optional.of(morningConfig));
        // First check sees empty
        // Isolated creator throws duplicate key
        // Service re-queries and finds the queue committed by the winner
        when(bookingQueueRepository.findBySessionDateAndSessionType(targetDate, SessionType.MORNING))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdByWinner));
        when(bookingQueueCreator.createQueue(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_queue_session_date"));
        when(queueEntryRepository.existsByQueueIdAndStudentId(100L, 1L)).thenReturn(false);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(inv -> {
            QueueEntry qe = inv.getArgument(0);
            qe.setId(501L);
            return qe;
        });
        when(queueEntryRepository.calculateWaitingPosition(eq(100L), eq(0), any(Instant.class), eq(501L))).thenReturn(1L);

        JoinQueueRequestDto request = new JoinQueueRequestDto(1L, targetDate, SessionType.MORNING);
        QueueEntryResponseDto response = service.joinQueue(request);

        assertThat(response.queueEntryId()).isEqualTo(501L);
        assertThat(response.status()).isEqualTo(QueueEntryStatus.WAITING);
    }

    @Test
    @DisplayName("Should allow student with zero credits to join the queue per credit rules")
    void shouldAllowStudentWithZeroCreditsToJoin() {
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 5, 0));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        when(studentRepository.findById(3L)).thenReturn(Optional.of(zeroCreditStudent));
        when(sessionConfigRepository.findBySessionType(SessionType.MORNING)).thenReturn(Optional.of(morningConfig));
        when(bookingQueueRepository.findBySessionDateAndSessionType(targetDate, SessionType.MORNING)).thenReturn(Optional.of(queue));
        when(queueEntryRepository.existsByQueueIdAndStudentId(100L, 3L)).thenReturn(false);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(inv -> {
            QueueEntry qe = inv.getArgument(0);
            qe.setId(503L);
            return qe;
        });
        when(queueEntryRepository.calculateWaitingPosition(eq(100L), eq(1), any(Instant.class), eq(503L))).thenReturn(1L);

        JoinQueueRequestDto request = new JoinQueueRequestDto(3L, targetDate, SessionType.MORNING);
        QueueEntryResponseDto response = service.joinQueue(request);

        assertThat(response.studentId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(QueueEntryStatus.WAITING);
    }

    @Test
    @DisplayName("Should reject inactive student from joining the queue")
    void shouldRejectInactiveStudent() {
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 5, 0));
        BookingQueueService service = createService(clock);

        when(studentRepository.findById(4L)).thenReturn(Optional.of(inactiveStudent));

        JoinQueueRequestDto request = new JoinQueueRequestDto(4L, targetDate, SessionType.MORNING);

        assertThatThrownBy(() -> service.joinQueue(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("Should reject duplicate queue entry for the same student")
    void shouldRejectDuplicateQueueEntry() {
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 5, 0));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(activeStudent1));
        when(sessionConfigRepository.findBySessionType(SessionType.MORNING)).thenReturn(Optional.of(morningConfig));
        when(bookingQueueRepository.findBySessionDateAndSessionType(targetDate, SessionType.MORNING)).thenReturn(Optional.of(queue));
        when(queueEntryRepository.existsByQueueIdAndStudentId(100L, 1L)).thenReturn(true);

        JoinQueueRequestDto request = new JoinQueueRequestDto(1L, targetDate, SessionType.MORNING);

        assertThatThrownBy(() -> service.joinQueue(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in this booking queue");
    }

    @Test
    @DisplayName("Should prevent admitting a second student while 60-second window is currently active")
    void shouldBlockAdmissionWhileActiveOpportunityInProgress() {
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 14, 21, 5, 0);
        Instant admittedTime = t0.atZone(zoneId).toInstant();

        // Current time is t0 + 30 seconds (opportunity still active with 30s remaining)
        Clock clock = createFixedClock(t0.plusSeconds(30));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        QueueEntry activeEntry = new QueueEntry(queue, activeStudent1, admittedTime);
        activeEntry.setId(501L);
        activeEntry.admit(admittedTime);

        when(bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(targetDate, SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStatus(100L, QueueEntryStatus.ADMITTED))
                .thenReturn(Optional.of(activeEntry));
        when(queueEntryRepository.countByQueueIdAndStatus(100L, QueueEntryStatus.WAITING)).thenReturn(1L);

        AdmitNextResponseDto response = service.admitNextStudent(targetDate, SessionType.MORNING);

        assertThat(response.studentAdmitted()).isFalse();
        assertThat(response.remainingSeconds()).isEqualTo(30L);
        assertThat(response.studentId()).isNull(); // Privacy: do not reveal currently active student
        assertThat(response.studentName()).isNull();
        assertThat(response.message()).contains("currently active");
    }

    @Test
    @DisplayName("Should expire active opportunity at 61 seconds and automatically admit next student in FIFO order in same call")
    void shouldExpireOpportunityAndAdmitNextStudent() {
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 14, 21, 5, 0);
        Instant admittedTime = t0.atZone(zoneId).toInstant();

        // Current time is t0 + 61 seconds (opportunity has expired)
        Clock clock = createFixedClock(t0.plusSeconds(61));
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        QueueEntry expiredEntry = new QueueEntry(queue, activeStudent1, admittedTime);
        expiredEntry.setId(501L);
        expiredEntry.admit(admittedTime);

        QueueEntry nextInLine = new QueueEntry(queue, activeStudent2, admittedTime.plusSeconds(5));
        nextInLine.setId(502L);

        when(bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(targetDate, SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStatus(100L, QueueEntryStatus.ADMITTED))
                .thenReturn(Optional.of(expiredEntry));
        when(queueEntryRepository.findWaitingEntriesOrderedByPriority(100L, QueueEntryStatus.WAITING))
                .thenReturn(List.of(nextInLine));
        when(queueEntryRepository.countByQueueIdAndStatus(100L, QueueEntryStatus.WAITING)).thenReturn(0L);

        AdmitNextResponseDto response = service.admitNextStudent(targetDate, SessionType.MORNING);

        assertThat(expiredEntry.getStatus()).isEqualTo(QueueEntryStatus.EXPIRED);
        assertThat(response.studentAdmitted()).isTrue();
        assertThat(response.studentId()).isEqualTo(2L);
        assertThat(response.studentName()).isEqualTo("Bob Student");
        assertThat(response.remainingSeconds()).isEqualTo(60L);
        assertThat(nextInLine.getStatus()).isEqualTo(QueueEntryStatus.ADMITTED);
    }

    @Test
    @DisplayName("Should admit student with credits before student with zero credits even if zero credits joined earlier")
    void shouldAdmitStudentWithCreditsBeforeZeroCreditStudentEvenIfJoinedEarlier() {
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 14, 21, 5, 0);
        Clock clock = createFixedClock(t0);
        BookingQueueService service = createService(clock);

        BookingQueue queue = new BookingQueue(morningConfig, SessionType.MORNING, targetDate);
        queue.setId(100L);

        // zeroCreditStudent joined at t0 - 10s (earlier)
        QueueEntry zeroCreditEntry = new QueueEntry(queue, zeroCreditStudent, t0.minusSeconds(10).atZone(zoneId).toInstant());
        zeroCreditEntry.setId(501L);

        // activeStudent1 (weeklyCredits = 6) joined at t0 - 5s (later)
        QueueEntry creditEntry = new QueueEntry(queue, activeStudent1, t0.minusSeconds(5).atZone(zoneId).toInstant());
        creditEntry.setId(502L);

        when(bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(targetDate, SessionType.MORNING))
                .thenReturn(Optional.of(queue));
        when(queueEntryRepository.findByQueueIdAndStatus(100L, QueueEntryStatus.ADMITTED))
                .thenReturn(Optional.empty());

        // findWaitingEntriesOrderedByPriority returns creditEntry first due to credit tier 0 vs 1
        when(queueEntryRepository.findWaitingEntriesOrderedByPriority(100L, QueueEntryStatus.WAITING))
                .thenReturn(List.of(creditEntry, zeroCreditEntry));
        when(queueEntryRepository.countByQueueIdAndStatus(100L, QueueEntryStatus.WAITING)).thenReturn(1L);

        AdmitNextResponseDto response = service.admitNextStudent(targetDate, SessionType.MORNING);

        // activeStudent1 with credits should be admitted first despite joining later
        assertThat(response.studentAdmitted()).isTrue();
        assertThat(response.studentId()).isEqualTo(1L);
        assertThat(response.studentName()).isEqualTo("Alice Student");
        assertThat(creditEntry.getStatus()).isEqualTo(QueueEntryStatus.ADMITTED);
        assertThat(zeroCreditEntry.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
    }

    @Test
    @DisplayName("Should enforce evening queue schedule: open at 12:00 PM same day, admissions at 12:05 PM")
    void shouldEnforceEveningQueueSchedule() {
        SessionConfig eveningConfig = new SessionConfig(
                SessionType.EVENING,
                LocalTime.of(17, 0),
                LocalTime.of(21, 30),
                30,
                LocalTime.of(12, 0),
                LocalTime.of(12, 5),
                0
        );
        eveningConfig.setId(20L);

        // Before 12:00 on session date (11:59:59)
        Clock clockBeforeOpen = createFixedClock(LocalDateTime.of(2026, 10, 15, 11, 59, 59));
        BookingQueueService serviceBeforeOpen = createService(clockBeforeOpen);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(activeStudent1));
        when(sessionConfigRepository.findBySessionType(SessionType.EVENING)).thenReturn(Optional.of(eveningConfig));

        JoinQueueRequestDto request = new JoinQueueRequestDto(1L, targetDate, SessionType.EVENING);
        assertThatThrownBy(() -> serviceBeforeOpen.joinQueue(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not opened yet");

        // Between 12:00 and 12:05 (12:02:00): admissions not started
        Clock clockBeforeAdmit = createFixedClock(LocalDateTime.of(2026, 10, 15, 12, 2, 0));
        BookingQueueService serviceBeforeAdmit = createService(clockBeforeAdmit);

        BookingQueue eveningQueue = new BookingQueue(eveningConfig, SessionType.EVENING, targetDate);
        eveningQueue.setId(200L);

        when(bookingQueueRepository.findBySessionDateAndSessionTypeForUpdate(targetDate, SessionType.EVENING))
                .thenReturn(Optional.of(eveningQueue));
        when(queueEntryRepository.countByQueueIdAndStatus(200L, QueueEntryStatus.WAITING)).thenReturn(2L);

        AdmitNextResponseDto admitResponse = serviceBeforeAdmit.admitNextStudent(targetDate, SessionType.EVENING);
        assertThat(admitResponse.studentAdmitted()).isFalse();
        assertThat(admitResponse.message()).contains("Booking has not started yet");
    }

    @Test
    @DisplayName("Should reject unverified student from joining the queue")
    void shouldRejectUnverifiedStudent() {
        Clock clock = createFixedClock(LocalDateTime.of(2026, 10, 14, 21, 5, 0));
        BookingQueueService service = createService(clock);

        Student unverifiedStudent = new Student("Unverified", "unverified@university.edu", "S10099", Role.STUDENT);
        unverifiedStudent.setId(99L);
        unverifiedStudent.setEmailVerified(false);

        when(studentRepository.findById(99L)).thenReturn(Optional.of(unverifiedStudent));

        JoinQueueRequestDto request = new JoinQueueRequestDto(99L, targetDate, SessionType.MORNING);

        assertThatThrownBy(() -> service.joinQueue(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verified university email");
    }
}
